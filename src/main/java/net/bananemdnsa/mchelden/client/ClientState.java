package net.bananemdnsa.mchelden.client;

import net.bananemdnsa.mchelden.network.StateSyncPayload;
import net.bananemdnsa.mchelden.state.Phase;
import net.bananemdnsa.mchelden.state.PlayerState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;

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

    /** Dauer der Herzverlust-Animation im HUD, in Ticks. */
    public static final int LOSS_ANIMATION_TICKS = 20;
    /** Dauer des bildschirmfüllenden Effekts. Laueft laenger, damit der Moment Gewicht bekommt. */
    public static final int LOSS_OVERLAY_TICKS = 34;

    private static int lossAnimationTicks;
    private static int lossOverlayTicks;

    /**
     * Startet die Verlust-Animation. Solange sie läuft, zeigt das HUD das verlorene Herz
     * noch — es zerspringt vor deinen Augen, statt einfach schon weg zu sein.
     */
    public static void onHeartLost(int remaining) {
        hearts = remaining;
        lossAnimationTicks = LOSS_ANIMATION_TICKS;
        lossOverlayTicks = LOSS_OVERLAY_TICKS;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.playSound(SoundEvents.GLASS_BREAK, 0.7f, 0.5f);
            player.playSound(SoundEvents.ALLAY_DEATH, 0.8f, 0.6f);
        }
    }

    public static void tick() {
        if (lossAnimationTicks > 0) {
            lossAnimationTicks--;
        }
        if (lossOverlayTicks > 0) {
            lossOverlayTicks--;
        }
    }

    public static boolean isLossOverlayRunning() {
        return lossOverlayTicks > 0;
    }

    /** 1.0 am Anfang des Overlays, 0.0 am Ende. */
    public static float lossOverlayProgress(float partialTick) {
        return Math.max(0f, lossOverlayTicks - partialTick) / LOSS_OVERLAY_TICKS;
    }

    public static boolean isLossAnimationRunning() {
        return lossAnimationTicks > 0;
    }

    /**
     * 1.0 am Anfang der Animation, 0.0 am Ende.
     *
     * <p>Der Teiltick muss mit hinein, sonst ruckeln die Bruchstücke im Tick-Takt statt
     * flüssig zu fliegen.
     */
    public static float lossAnimationProgress(float partialTick) {
        return Math.max(0f, lossAnimationTicks - partialTick) / LOSS_ANIMATION_TICKS;
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
        lossAnimationTicks = 0;
        lossOverlayTicks = 0;
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
