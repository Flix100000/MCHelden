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
    /**
     * Wie oft der Server den Balken des Clients nachzieht.
     *
     * <p>Der Client zaehlt selbst herunter, und seine Ticks laufen in echter Zeit, waehrend
     * die Serverticks hier unter Last dahinter zurueckfallen. Ohne Korrektur war der Balken
     * leer, waehrend der Kampf laut Server noch lief — Kisten und Safezone blieben dann
     * noch Sekunden lang gesperrt, ohne dass etwas davon zu sehen war.
     */
    private static final int CORRECTION_GAP = 20;

    private static final Map<UUID, Tag> TAGS = new ConcurrentHashMap<>();

    /**
     * Gegner beim letzten Kampf- oder Duelltod, bis die Todesnachricht erzeugt wurde.
     *
     * <p>Minecraft feuert das Todes-Event vor der Nachrichtenerzeugung. Bis die Nachricht
     * gebaut wird, ist der Timer laengst geraeumt — der Gegner muss also zwischengelagert
     * werden, sonst steht in der Nachricht niemand mehr.
     */
    private static final Map<UUID, Death> DEATHS = new ConcurrentHashMap<>();

    /**
     * Ein vorgemerkter Tod.
     *
     * @param opponent wer zuletzt zugeschlagen hat
     * @param duel war es ein Duell? Dann liest die Nachricht sich anders
     */
    public record Death(String opponent, boolean duel) {
    }

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
        tag.timer.hit();
        tag.opponent = opponent;
        tag.opponentId = opponentId;

        NetworkHandler.sendCombatHit(player);
    }

    /**
     * Setzt einen laufenden Timer als Combat-Timer ein.
     *
     * <p>Fuer das Platzen eines Duells: der Duell-Timer laeuft danach als Combat-Timer
     * weiter, mit Restzeit und Trefferzaehler. Ohne diese Uebernahme stuende ein Duellant,
     * dessen Duell ein Dritter zerschlagen hat, ploetzlich ganz ohne Timer da — und koennte
     * sich mitten aus einem verlorenen Kampf ausloggen.
     */
    public static void adopt(ServerPlayer player, HitTimer timer, String opponent,
                             @Nullable UUID opponentId) {
        Tag tag = TAGS.computeIfAbsent(player.getUUID(), uuid -> new Tag());
        tag.timer.adopt(timer);
        tag.opponent = opponent;
        tag.opponentId = opponentId;

        // Wie ein Treffer angesagt: fuer den Client faengt hier ein Combat-Timer an, wo
        // gerade noch ein Duell-Timer stand.
        NetworkHandler.sendCombatHit(player);
    }

    public static boolean isInCombat(UUID uuid) {
        return TAGS.containsKey(uuid);
    }

    public static int remainingTicks(UUID uuid) {
        Tag tag = TAGS.get(uuid);
        return tag != null ? tag.timer.remainingTicks() : 0;
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
    public static void recordCombatDeath(UUID uuid, @Nullable String opponent) {
        if (opponent != null && !opponent.isEmpty()) {
            DEATHS.put(uuid, new Death(opponent, false));
        }
    }

    /** Dasselbe fuer einen Tod im Duell. */
    public static void recordDuelDeath(UUID uuid, @Nullable String opponent) {
        if (opponent != null && !opponent.isEmpty()) {
            DEATHS.put(uuid, new Death(opponent, true));
        }
    }

    /** Holt den letzten vorgemerkten Tod und verbraucht ihn dabei. */
    @Nullable
    public static Death consumeDeath(UUID uuid) {
        return DEATHS.remove(uuid);
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
            if (!entry.getValue().timer.tick()) {
                continue;
            }

            entries.remove();
            ItemQuota.reset(entry.getKey());

            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                NetworkHandler.sendCombat(player);
            }
        }

        correct(server);
    }

    /** Zieht die Balken aller Kaempfenden nach, einmal pro Sekunde. */
    private static void correct(MinecraftServer server) {
        if (server.getTickCount() % CORRECTION_GAP != 0) {
            return;
        }

        for (UUID uuid : TAGS.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                NetworkHandler.sendCombat(player);
            }
        }
    }

    private static final class Tag {
        private final HitTimer timer = new HitTimer();
        @Nullable
        private String opponent;
        @Nullable
        private UUID opponentId;
    }
}
