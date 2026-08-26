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
        extend(victim, attacker.getGameProfile().getName());
        extend(attacker, victim.getGameProfile().getName());
    }

    /**
     * Verlängert den Timer um einen Treffer, gedeckelt bei drei Minuten.
     *
     * @param opponent Name des Gegenübers, für Todesnachricht und Ansage
     */
    public static void extend(ServerPlayer player, String opponent) {
        Tag tag = TAGS.computeIfAbsent(player.getUUID(), uuid -> new Tag());
        tag.ticks = Math.min(MAX_TICKS, tag.ticks + HIT_TICKS);
        tag.opponent = opponent;

        NetworkHandler.sendCombat(player, tag.ticks);
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

    /** Nimmt den Timer weg, ohne dass er abgelaufen ist. Für {@code /helden combat clear}. */
    public static void clear(ServerPlayer player) {
        if (TAGS.remove(player.getUUID()) != null) {
            NetworkHandler.sendCombat(player, 0);
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

            if (--tag.ticks > 0) {
                continue;
            }

            entries.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                NetworkHandler.sendCombat(player, 0);
            }
        }
    }

    private static final class Tag {
        private int ticks;
        @Nullable
        private String opponent;
    }
}
