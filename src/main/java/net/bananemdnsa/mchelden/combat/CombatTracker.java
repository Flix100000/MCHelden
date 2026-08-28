package net.bananemdnsa.mchelden.combat;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.bananemdnsa.mchelden.network.NetworkHandler;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Der Combat-Timer. Kern des gesamten Regelwerks: an ihm hängen Herzverlust,
 * GUI-Sperre, Item-Kontingent und der Safezone-Zutritt.
 *
 * <p>Bewusst nicht persistent. Ein Logout mit laufendem Timer zählt als Tod, es kann also
 * gar keinen gespeicherten Kampfzustand geben, den man beim Neustart wiederherstellen müsste.
 */
public final class CombatTracker {
    /** Was ein einzelner Treffer draufpackt. */
    public static final int HIT_TICKS = 30 * 20;
    /** Obergrenze, egal wie oft getroffen wird. */
    public static final int MAX_TICKS = 180 * 20;
    /** Nach dem ersten Treffer legt erst wieder jeder fuenfte Zeit nach. */
    public static final int HITS_PER_EXTENSION = 5;
    /** So lange darf eine Pause zwischen zwei Treffern sein, ohne die Serie zu reissen. */
    public static final int HIT_WINDOW_TICKS = 30 * 20;

    private static final Map<UUID, Tag> TAGS = new ConcurrentHashMap<>();

    /**
     * Gegner beim letzten Kampftod, bis die Todesnachricht erzeugt wurde.
     *
     * <p>Minecraft feuert das Todes-Event vor der Nachrichtenerzeugung. Bis die Nachricht
     * gebaut wird, ist der Timer laengst geraeumt — der Gegner muss also zwischengelagert
     * werden, sonst steht in der Nachricht niemand mehr.
     */
    private static final Map<UUID, String> DEATH_OPPONENTS = new ConcurrentHashMap<>();

    private CombatTracker() {
    }

    /** Ein Spieler trifft einen anderen: beide hängen danach im Timer. */
    public static void onPlayerHit(ServerPlayer attacker, ServerPlayer victim) {
        extend(victim, attacker.getGameProfile().getName(), attacker.getUUID());
        extend(attacker, victim.getGameProfile().getName(), victim.getUUID());
    }

    /**
     * Zählt den Treffer und verlängert den Timer, falls dieser Treffer Zeit nachlegt.
     *
     * <p>Der Gegner wird bei jedem Treffer nachgezogen, auch bei denen dazwischen: in der
     * Todesnachricht soll stehen, wer zuletzt zugeschlagen hat, und nicht, wer zufällig
     * den fünften Schlag gelandet hat.
     *
     * @param opponent Name des Gegenübers, für Todesnachricht und Ansage
     * @param opponentId UUID des Gegenübers, {@code null} bei simulierten Treffern
     */
    public static void extend(ServerPlayer player, String opponent, @Nullable UUID opponentId) {
        Tag tag = TAGS.computeIfAbsent(player.getUUID(), uuid -> new Tag());
        tag.hits = nextHitNumber(tag.hits, tag.idleTicks);
        tag.idleTicks = 0;
        tag.ticks = extendedTicks(tag.ticks, tag.hits);
        tag.opponent = opponent;
        tag.opponentId = opponentId;

        NetworkHandler.sendCombat(player);
    }

    /**
     * Der Timerstand nach dem {@code hitNumber}-ten Treffer eines Kampfes.
     *
     * <p>Zeit gibt es beim ersten Treffer und danach bei jedem fünften. Würde jeder
     * Treffer zählen, wäre der Deckel schon nach sechs Schlägen erreicht — ein
     * gewöhnlicher Kampf hätte dann beide Seiten drei Minuten lang festgehalten.
     *
     * <p>Gezählt wird pro Spieler über alle Gegner zusammen: wer von zwei Leuten
     * gleichzeitig geschlagen wird, kommt schneller an den fünften Treffer.
     */
    static int extendedTicks(int currentTicks, int hitNumber) {
        boolean addsTime = hitNumber == 1 || hitNumber % HITS_PER_EXTENSION == 0;
        return addsTime ? Math.min(MAX_TICKS, currentTicks + HIT_TICKS) : currentTicks;
    }

    /**
     * Der wievielte Treffer einer Serie das ist.
     *
     * <p>Eine Serie hält nur, solange getroffen wird: liegen 30 Sekunden zwischen zwei
     * Treffern, fängt die Zählung wieder bei eins an. Sonst könnte man vier Treffer
     * über eine halbe Stunde verteilt sammeln und den fünften als Verlängerung kassieren.
     *
     * <p>Der Neustart kann auch mitten im laufenden Timer passieren — der reicht bis zu
     * drei Minuten, das Fenster nur dreissig Sekunden.
     *
     * @param ticksSinceLastHit Ticks seit dem letzten Treffer, 0 beim allerersten
     */
    static int nextHitNumber(int previousHits, int ticksSinceLastHit) {
        return ticksSinceLastHit >= HIT_WINDOW_TICKS ? 1 : previousHits + 1;
    }

    public static boolean isInCombat(UUID uuid) {
        return TAGS.containsKey(uuid);
    }

    public static int remainingTicks(UUID uuid) {
        Tag tag = TAGS.get(uuid);
        return tag != null ? tag.ticks : 0;
    }

    /** Wer zuletzt zugeschlagen hat, oder {@code null} ausserhalb des Kampfes. */
    @Nullable
    public static String opponentOf(UUID uuid) {
        Tag tag = TAGS.get(uuid);
        return tag != null ? tag.opponent : null;
    }

    /**
     * Dasselbe als UUID.
     *
     * <p>Die Bounty-Auflösung darf nicht an einem Namensvergleich hängen: Namen sind
     * änderbar und im Zustand nur als Kopie gespeichert.
     */
    @Nullable
    public static UUID opponentIdOf(UUID uuid) {
        Tag tag = TAGS.get(uuid);
        return tag != null ? tag.opponentId : null;
    }

    /** Nimmt den Timer weg, ohne dass er abgelaufen ist. Für {@code /helden combat clear}. */
    public static void clear(ServerPlayer player) {
        if (TAGS.remove(player.getUUID()) != null) {
            ItemQuota.reset(player.getUUID());
            NetworkHandler.sendCombat(player);
        }
    }

    /** Merkt sich den Gegner fuer die gleich folgende Todesnachricht. */
    public static void recordCombatDeath(UUID uuid, String opponent) {
        if (opponent != null && !opponent.isEmpty()) {
            DEATH_OPPONENTS.put(uuid, opponent);
        }
    }

    /** Holt den Gegner des letzten Kampftods und verbraucht ihn dabei. */
    @Nullable
    public static String consumeCombatDeath(UUID uuid) {
        return DEATH_OPPONENTS.remove(uuid);
    }

    /** Vergisst einen Spieler ersatzlos, ohne ihm etwas zu schicken. */
    public static void forget(UUID uuid) {
        TAGS.remove(uuid);
    }

    /** Zählt alle laufenden Timer herunter und meldet, wer den Kampf verlässt. */
    public static void tick(MinecraftServer server) {
        if (TAGS.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, Tag>> entries = TAGS.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Tag> entry = entries.next();
            Tag tag = entry.getValue();
            tag.idleTicks++;

            if (--tag.ticks > 0) {
                continue;
            }

            entries.remove();
            ItemQuota.reset(entry.getKey());

            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                NetworkHandler.sendCombat(player);
            }
        }
    }

    private static final class Tag {
        private int ticks;
        /** Treffer der laufenden Serie, siehe {@link CombatTracker#nextHitNumber(int, int)}. */
        private int hits;
        /** Ticks seit dem letzten Treffer. Ab {@link #HIT_WINDOW_TICKS} reisst die Serie. */
        private int idleTicks;
        @Nullable
        private String opponent;
        @Nullable
        private UUID opponentId;
    }
}
