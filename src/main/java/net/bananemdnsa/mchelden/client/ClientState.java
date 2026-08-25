package net.bananemdnsa.mchelden.client;

import net.bananemdnsa.mchelden.network.StateSyncPayload;
import net.bananemdnsa.mchelden.state.Phase;
import net.bananemdnsa.mchelden.state.PlayerState;

/**
 * Letzter vom Server empfangener Zustand des eigenen Spielers. Quelle fuer alle HUDs.
 * Wird nur auf dem Client geladen.
 */
public final class ClientState {
    private static int hearts = PlayerState.DEFAULT_HEARTS;
    private static String bountyTargetName = "";
    private static boolean bountyResolved;
    private static int playtimeRemainingSeconds = PlayerState.DAILY_PLAYTIME_SECONDS;
    private static Phase phase = Phase.AUFBAU;

    private ClientState() {
    }

    public static void accept(StateSyncPayload payload) {
        hearts = payload.hearts();
        bountyTargetName = payload.bountyTargetName();
        bountyResolved = payload.bountyResolved();
        playtimeRemainingSeconds = payload.playtimeRemainingSeconds();
        phase = Phase.byId(payload.phaseId());
    }

    /** Beim Verlassen einer Welt aufrufen, damit nichts in die naechste Session leckt. */
    public static void reset() {
        hearts = PlayerState.DEFAULT_HEARTS;
        bountyTargetName = "";
        bountyResolved = false;
        playtimeRemainingSeconds = PlayerState.DAILY_PLAYTIME_SECONDS;
        phase = Phase.AUFBAU;
    }

    public static int getHearts() {
        return hearts;
    }

    public static boolean hasBounty() {
        return !bountyTargetName.isEmpty();
    }

    public static String getBountyTargetName() {
        return bountyTargetName;
    }

    public static boolean isBountyResolved() {
        return bountyResolved;
    }

    public static int getPlaytimeRemainingSeconds() {
        return playtimeRemainingSeconds;
    }

    public static Phase getPhase() {
        return phase;
    }
}
