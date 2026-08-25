package net.bananemdnsa.mchelden.hearts;

import javax.annotation.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public final class HeartEvents {
    private HeartEvents() {
    }

    /**
     * Herzverlust beim Tod durch einen Spieler.
     *
     * <p>Vorläufige Definition: der Verursacher des Schadens muss ein anderer Spieler sein.
     * Etappe 2 ersetzt das durch den Combat-Timer — dann zählt jeder Tod mit laufendem
     * Timer, auch der Sturz in eine Schlucht auf der Flucht.
     */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }

        MinecraftServer server = victim.getServer();
        if (server == null) {
            return;
        }

        Player killer = killerOf(event, victim);
        if (killer == null) {
            return;
        }

        HeartManager.loseHeart(server, victim.getUUID(), killer.getGameProfile().getName());
    }

    /** Liefert den verursachenden Spieler, oder {@code null} wenn keiner beteiligt war. */
    @Nullable
    private static Player killerOf(LivingDeathEvent event, ServerPlayer victim) {
        Entity source = event.getSource().getEntity();

        // Bei Projektilen liefert getEntity() bereits den Schützen, nicht den Pfeil.
        if (source instanceof Player player && !player.getUUID().equals(victim.getUUID())) {
            return player;
        }
        return null;
    }
}
