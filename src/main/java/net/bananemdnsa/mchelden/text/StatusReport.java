package net.bananemdnsa.mchelden.text;

import java.util.UUID;

import net.bananemdnsa.mchelden.bounty.BountyManager;
import net.bananemdnsa.mchelden.playtime.PlaytimeTracker;
import net.bananemdnsa.mchelden.state.GameState;
import net.bananemdnsa.mchelden.state.PlayerState;
import net.bananemdnsa.mchelden.state.PlayerStateStore;
import net.bananemdnsa.mchelden.state.Side;
import net.bananemdnsa.mchelden.world.DividerWall;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Der Statusblock, den jeder beim Betreten bekommt.
 *
 * <p>Seite, Herzen, Phase, Spielzeit und Bounty auf einmal. Ohne ihn muss sich jeder aus
 * einer Wand, einer Herzreihe und einem Fragezeichen selbst zusammenreimen, wo er steht —
 * und wer zwei Tage weg war, weiss ohnehin nicht mehr, welche Phase gerade laeuft.
 *
 * <p>Bei jedem Join, nicht nur beim ersten: der Zustand aendert sich zwischen zwei
 * Sitzungen, und genau dann will man ihn wissen.
 */
public final class StatusReport {

    private StatusReport() {
    }

    public static void send(MinecraftServer server, ServerPlayer player) {
        PlayerState state = PlayerStateStore.get(server).getOrCreate(player.getUUID());

        player.sendSystemMessage(HeldenText.welcomeHeader());

        Side side = state.getSide();
        if (side != null) {
            player.sendSystemMessage(HeldenText.infoLine("mchelden.welcome.side", side.getDisplayName()));
        }

        player.sendSystemMessage(HeldenText.infoLine("mchelden.welcome.hearts",
                HeldenText.welcomeHearts(state.getHearts())));
        player.sendSystemMessage(HeldenText.infoLine("mchelden.welcome.phase",
                GameState.get(server).getPhase().getDisplayName()));
        player.sendSystemMessage(HeldenText.infoLine("mchelden.welcome.playtime",
                playtime(server, player, state)));
        player.sendSystemMessage(HeldenText.infoLine("mchelden.welcome.bounty",
                bounty(server, state)));

        if (DividerWall.isUp(server)) {
            player.sendSystemMessage(HeldenText.welcomeWall());
        }
    }

    /** Die Restzeit — oder der Hinweis, dass hier gar kein Limit gilt. */
    private static Component playtime(MinecraftServer server, ServerPlayer player, PlayerState state) {
        if (!PlaytimeTracker.isLimited(server, player)) {
            return HeldenText.playtimeExempt();
        }

        int seconds = PlaytimeTracker.remainingSeconds(state.getPlaytimeUsedSeconds());
        return HeldenText.playtimeLeft(String.format("%d:%02d", seconds / 60, seconds % 60));
    }

    /**
     * Der Bounty-Stand.
     *
     * <p>Steht noch ein nachgeholtes Gluecksrad aus, wird das Ziel <b>nicht</b> verraten —
     * sonst stuende es hier, bevor der Streifen es preisgibt.
     */
    private static Component bounty(MinecraftServer server, PlayerState state) {
        if (BountyManager.isRollPending(server, state.getUuid())) {
            return HeldenText.welcomeBountyPending();
        }

        UUID target = state.getBountyTarget();
        if (target != null) {
            return Component.literal(BountyManager.nameOf(server, target));
        }

        return state.isBountyResolved() ? HeldenText.bountyResolved() : HeldenText.welcomeBountyPending();
    }
}
