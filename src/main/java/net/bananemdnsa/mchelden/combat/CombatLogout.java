package net.bananemdnsa.mchelden.combat;

import net.bananemdnsa.mchelden.bounty.BountyManager;
import net.bananemdnsa.mchelden.hearts.HeartManager;
import net.bananemdnsa.mchelden.text.HeldenText;
import net.bananemdnsa.mchelden.state.PlayerState;
import net.bananemdnsa.mchelden.state.PlayerStateStore;

import java.util.List;

import net.bananemdnsa.mchelden.grave.GraveEvents;
import net.bananemdnsa.mchelden.grave.GraveNotice;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Ausloggen mit laufendem Combat-Timer.
 *
 * <p>Bewusst ein eigener, ausbuchstabierter Ablauf statt eines Aufrufs von {@code die()}.
 * Ein Tod besteht aus mehr als dem Sterben: Nachricht, Beute, Respawn. Beim Ausloggen wird
 * der Spieler aber gerade aus der Welt entfernt, und was danach käme, findet nie statt —
 * das Ergebnis war ein Zwischending aus tot und lebendig, mit verlorenem Inventar, aber
 * ohne Nachricht und ohne Rückkehr am Spawnpunkt.
 *
 * <p>Der Respawn selbst lässt sich in dem Moment nicht nachholen, deshalb wird er vorgemerkt
 * und beim nächsten Join ausgeführt. Die Vormerkung ist persistent, weil dazwischen ein
 * Serverneustart liegen kann.
 */
public final class CombatLogout {
    private CombatLogout() {
    }

    /** Behandelt das Ausloggen im Kampf als Tod. */
    public static void handle(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        String opponent = CombatTracker.opponentOf(player.getUUID());
        java.util.UUID opponentId = CombatTracker.opponentIdOf(player.getUUID());
        CombatTracker.recordCombatDeath(player.getUUID(), opponent);
        CombatTracker.clear(player);

        announce(server, player, opponent);

        // Das Grab muss hier entstehen, nicht beim Wiederkommen: sonst haette der Fluechtende
        // seine Sachen noch, und der Gegner ginge leer aus. Ausloggen waere sonst die
        // guenstigere Art zu sterben.
        if (player.level() instanceof ServerLevel level) {
            List<ItemStack> keep = GraveEvents.bury(player, level,
                    GraveEvents.wornArmor(player), GraveEvents.carriedItems(player));

            // Die behaltene Haelfte kommt direkt zurueck ins Inventar statt in eine
            // Zwischenablage: sie wird beim Ausloggen mitgespeichert und ueberlebt damit
            // auch einen Serverneustart.
            keep.forEach(stack -> player.getInventory().add(stack));
        }

        PlayerStateStore store = PlayerStateStore.get(server);
        store.getOrCreate(player.getUUID()).setPendingRespawn(true);
        store.setDirty();

        // Wer sich seinem Bounty-Partner ausloggt, hat den Bounty-Kampf verloren. Der
        // Logout ist ein Tod wie jeder andere, also loest er auch das Bounty auf.
        if (BountyManager.resolve(server, player.getUUID(), opponentId)) {
            return;
        }

        HeartManager.loseHeart(server, player.getUUID(), opponent != null ? opponent : "");
    }

    /**
     * Holt den vorgemerkten Respawn nach.
     *
     * @return true, wenn tatsächlich respawnt wurde
     */
    public static boolean deliverPendingRespawn(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        PlayerStateStore store = PlayerStateStore.get(server);
        PlayerState state = store.getOrCreate(player.getUUID());
        if (!state.isPendingRespawn()) {
            return false;
        }

        state.setPendingRespawn(false);
        store.setDirty();

        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.clearFire();
        teleportToRespawn(server, player);

        // Erst nach dem Teleport, damit der Abstand zum Grab vom Respawnpunkt aus
        // gemessen wird und nicht von der Stelle, an der der Spieler ausgeloggt ist.
        GraveNotice.send(player);
        return true;
    }

    /** Bett oder Weltspawn, genau wie nach einem gewöhnlichen Tod. */
    private static void teleportToRespawn(MinecraftServer server, ServerPlayer player) {
        ServerLevel level = server.getLevel(player.getRespawnDimension());
        BlockPos bed = player.getRespawnPosition();

        if (level != null && bed != null) {
            player.teleportTo(level, bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
            return;
        }

        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                player.getYRot(), player.getXRot());
    }

    /** Ohne {@code die()} gibt es keine Todesnachricht — die muss selbst verschickt werden. */
    private static void announce(MinecraftServer server, ServerPlayer player, String opponent) {
        server.getPlayerList().broadcastSystemMessage(
                HeldenText.combatLogout(player.getGameProfile().getName(),
                        opponent != null ? opponent : ""), false);
    }

}
