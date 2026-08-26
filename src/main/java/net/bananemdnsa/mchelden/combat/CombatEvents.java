package net.bananemdnsa.mchelden.combat;

import net.bananemdnsa.mchelden.hearts.HeartManager;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class CombatEvents {
    private CombatEvents() {
    }

    /**
     * Jeder Treffer zwischen zwei Spielern verlängert den Timer bei beiden.
     *
     * <p>Bei Projektilen liefert {@code getEntity()} bereits den Schützen, ein Pfeil aus
     * hundert Metern zählt also genauso wie ein Schwertstreich.
     */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)
                || !(event.getSource().getEntity() instanceof ServerPlayer attacker)
                || attacker.getUUID().equals(victim.getUUID())) {
            return;
        }

        CombatTracker.onPlayerHit(attacker, victim);
    }

    /**
     * Die eigentliche Todesdefinition: wer mit laufendem Timer stirbt, ist durch einen
     * Spieler gestorben — egal, woran er tatsächlich gestorben ist.
     *
     * <p>Damit zählt auch der Sturz in eine Schlucht auf der Flucht. Genau dafür ist der
     * Timer da: man soll sich nicht aus einem Kampf herausstürzen können.
     */
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }

        MinecraftServer server = victim.getServer();
        if (server == null || !CombatTracker.isInCombat(victim.getUUID())) {
            return;
        }

        String killer = CombatTracker.opponentOf(victim.getUUID());
        CombatTracker.recordCombatDeath(victim.getUUID(), killer);

        // clear statt forget: der Client muss erfahren, dass der Kampf vorbei ist, sonst
        // zaehlt er seinen lokalen Stand weiter und zeigt den Balken nach dem Respawn noch.
        CombatTracker.clear(victim);
        HeartManager.loseHeart(server, victim.getUUID(), killer != null ? killer : "");
    }

    /**
     * Logout mit laufendem Timer zählt als Tod.
     *
     * <p>Ohne diese Regel ist der ganze Timer wertlos, weil jeder verlorene Kampf mit
     * Alt+F4 endet. Der Ablauf ist in {@link CombatLogout} ausbuchstabiert, statt einen
     * regulaeren Tod auszuloesen — beim Ausloggen wird der Spieler gerade entfernt, und
     * alles was nach dem Sterben kaeme faende dann nie statt.
     */
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !CombatTracker.isInCombat(player.getUUID())) {
            return;
        }

        CombatLogout.handle(player);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        CombatTracker.tick(event.getServer());
        HeartManager.tick(event.getServer());
    }
}
