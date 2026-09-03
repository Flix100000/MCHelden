package net.bananemdnsa.mchelden.combat;

import java.util.UUID;

import net.bananemdnsa.mchelden.bounty.BountyManager;
import net.bananemdnsa.mchelden.duel.DuelManager;
import net.bananemdnsa.mchelden.event.EventManager;
import net.bananemdnsa.mchelden.hearts.HeartManager;
import net.bananemdnsa.mchelden.phase.PhaseManager;
import net.bananemdnsa.mchelden.playtime.PlaytimeTracker;
import net.bananemdnsa.mchelden.world.BorderStorm;
import net.bananemdnsa.mchelden.world.DividerWall;
import net.bananemdnsa.mchelden.world.FinalWarBar;
import net.bananemdnsa.mchelden.world.SafeZone;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
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
     *
     * <p>Es zählen nur Treffer, die auch ankommen. In der Safezone wird der Schaden
     * abgesagt — ohne diese Prüfung startete dort der Timer, obwohl niemand Schaden
     * nimmt. Darum hängt der Listener auf {@link EventPriority#LOWEST}: erst sagt ab,
     * wer absagen will, dann wird gezählt.
     */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled()
                || !(event.getEntity() instanceof ServerPlayer victim)
                || !(event.getSource().getEntity() instanceof ServerPlayer attacker)
                || attacker.getUUID().equals(victim.getUUID())) {
            return;
        }

        // Die beiden Verabredeten unter sich: der Duell-Timer laeuft, der Combat-Timer
        // bleibt aus. Genau daran haengt, dass dieser Tod kein Herz kostet.
        if (DuelManager.arePartners(attacker.getUUID(), victim.getUUID())) {
            DuelManager.onHit(attacker, victim);
            return;
        }

        // Ein Dritter ist im Spiel. Beide Duelle platzen, bevor der Treffer normal zaehlt —
        // danach ueberschreibt der Treffer den Gegner der Beteiligten ohnehin richtig.
        DuelManager.breakUp(attacker);
        DuelManager.breakUp(victim);

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
        if (server == null) {
            return;
        }

        // Das Duell zuerst: es schliesst den Combat-Timer aus, und dieser Tod kostet kein
        // Herz. Grab, Itemsplit und XP laufen trotzdem — geschuetzt ist nur das Herz.
        if (DuelManager.onDeath(server, victim)) {
            return;
        }

        if (!CombatTracker.isInCombat(victim.getUUID())) {
            return;
        }

        String killer = CombatTracker.opponentOf(victim.getUUID());
        UUID killerId = CombatTracker.opponentIdOf(victim.getUUID());
        CombatTracker.recordCombatDeath(victim.getUUID(), killer);

        // clear statt forget: der Client muss erfahren, dass der Kampf vorbei ist, sonst
        // zaehlt er seinen lokalen Stand weiter und zeigt den Balken nach dem Respawn noch.
        CombatTracker.clear(victim);

        // War es der Bounty-Kampf, kostet der Tod kein Herz. Alles andere — Grab,
        // Itemsplit, XP — laeuft trotzdem: geschuetzt ist nur das Herz.
        if (BountyManager.resolve(server, victim.getUUID(), killerId)) {
            return;
        }

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
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Straffrei: im Duell stand kein Herz auf dem Spiel, es gibt also nichts, wovor ein
        // Logout schuetzen koennte. Raeumt nebenbei eine offene Anfrage weg.
        DuelManager.onLogout(player);

        if (CombatTracker.isInCombat(player.getUUID())) {
            CombatLogout.handle(player);
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        CombatTracker.tick(event.getServer());
        HeartManager.tick(event.getServer());
        BountyManager.tick(event.getServer());
        DuelManager.tick(event.getServer());
        PhaseManager.tick(event.getServer());
        FinalWarBar.tick(event.getServer());
        EventManager.tick(event.getServer());
        SafeZone.tickBurst(event.getServer());
        BorderStorm.tick(event.getServer());
        PlaytimeTracker.tick(event.getServer());
        DividerWall.tick(event.getServer());
    }
}
