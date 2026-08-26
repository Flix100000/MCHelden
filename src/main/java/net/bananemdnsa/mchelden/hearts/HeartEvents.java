package net.bananemdnsa.mchelden.hearts;

import net.bananemdnsa.mchelden.network.NetworkHandler;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Der Herzverlust selbst wird in {@code combat.CombatEvents} ausgelöst — dort, wo die
 * Todesdefinition sitzt. Hier bleibt nur, was mit dem Respawn zu tun hat.
 */
public final class HeartEvents {
    private HeartEvents() {
    }

    /**
     * Löst die vorgemerkte Verlust-Animation aus, sobald der Spieler wieder im Spiel ist.
     * Der Zustand selbst wurde schon beim Tod gesetzt, hier kommt nur die Darstellung.
     */
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) {
            return;
        }

        NetworkHandler.syncTo(player);
        HeartManager.deliverPendingLoss(player);
    }
}
