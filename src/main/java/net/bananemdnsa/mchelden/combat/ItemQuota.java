package net.bananemdnsa.mchelden.combat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.bananemdnsa.mchelden.network.NetworkHandler;
import net.bananemdnsa.mchelden.text.HeldenText;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Verbrauchskontingent im Kampf: Enderperlen und Spinnweben, je zwei Stacks.
 *
 * <p>Gilt nur, solange der Combat-Timer läuft, und wird mit ihm zurückgesetzt. Ausserhalb
 * des Kampfes darf jeder verbrauchen, soviel er will.
 *
 * <p>Goldäpfel stehen bewusst nicht auf der Liste: ohne Nether kommt jedes Gold aus normalem
 * Bergbau, und acht Barren pro Apfel begrenzen sie über die Welt schon selbst.
 */
public final class ItemQuota {
    public enum Kind {
        /** Enderperlen stapeln zu 16, zwei Stacks sind 32. */
        PEARL(32, "mchelden.combat.quota.pearl"),
        /** Spinnweben stapeln zu 64, zwei Stacks sind 128. Platzieren zaehlt als Verbrauch. */
        COBWEB(128, "mchelden.combat.quota.cobweb");

        private final int limit;
        private final String key;

        Kind(int limit, String key) {
            this.limit = limit;
            this.key = key;
        }

        public int limit() {
            return limit;
        }

        public String translationKey() {
            return key;
        }
    }

    private static final Map<UUID, int[]> USED = new ConcurrentHashMap<>();

    private ItemQuota() {
    }

    /**
     * Prüft, ob noch etwas übrig ist, ohne zu buchen.
     *
     * <p>Ausserhalb des Kampfes immer true — das Kontingent gilt nur im Kampf.
     */
    public static boolean hasLeft(ServerPlayer player, Kind kind) {
        if (!CombatTracker.isInCombat(player.getUUID())) {
            return true;
        }

        int[] counters = USED.get(player.getUUID());
        return counters == null || counters[kind.ordinal()] < kind.limit();
    }

    /** Bucht einen Verbrauch. Ausserhalb des Kampfes passiert nichts. */
    public static void consume(ServerPlayer player, Kind kind) {
        if (!CombatTracker.isInCombat(player.getUUID())) {
            return;
        }

        int[] counters = USED.computeIfAbsent(player.getUUID(), uuid -> new int[Kind.values().length]);
        counters[kind.ordinal()] = Math.min(kind.limit(), counters[kind.ordinal()] + 1);
        NetworkHandler.sendCombat(player);
    }

    /**
     * Prüft und bucht in einem Schritt.
     *
     * @return false, wenn das Kontingent erschöpft ist — dann darf der Vorgang nicht stattfinden
     */
    public static boolean tryUse(ServerPlayer player, Kind kind) {
        if (!hasLeft(player, kind)) {
            deny(player, kind);
            return false;
        }

        consume(player, kind);
        return true;
    }

    /** Meldet dem Spieler, dass nichts mehr übrig ist. */
    public static void refuse(ServerPlayer player, Kind kind) {
        deny(player, kind);
    }

    public static int remaining(UUID uuid, Kind kind) {
        int[] counters = USED.get(uuid);
        return counters == null ? kind.limit() : Math.max(0, kind.limit() - counters[kind.ordinal()]);
    }

    /** Setzt alle Kontingente auf aufgebraucht. Zum Testen des leeren Zustands. */
    public static void drain(ServerPlayer player) {
        int[] counters = USED.computeIfAbsent(player.getUUID(), uuid -> new int[Kind.values().length]);
        for (Kind kind : Kind.values()) {
            counters[kind.ordinal()] = kind.limit();
        }
        NetworkHandler.sendCombat(player);
    }

    /** Beim Ende des Kampfes aufrufen. Das Kontingent gehört zum einzelnen Kampf. */
    public static void reset(UUID uuid) {
        USED.remove(uuid);
    }

    private static void deny(ServerPlayer player, Kind kind) {
        player.playNotifySound(SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.6f, 0.8f);
        player.displayClientMessage(HeldenText.quotaEmpty(kind), true);
    }
}
