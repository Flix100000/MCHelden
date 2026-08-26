package net.bananemdnsa.mchelden.client;

import net.bananemdnsa.mchelden.network.StateSyncPayload;
import net.bananemdnsa.mchelden.state.Phase;
import net.bananemdnsa.mchelden.state.PlayerState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * Letzter vom Server empfangener Zustand des eigenen Spielers. Quelle fuer alle HUDs.
 * Wird nur auf dem Client geladen.
 */
public final class ClientState {
    /** Wie lange das Herz vor dem Bruch noch heil steht. Baut die Spannung auf. */
    public static final int LOSS_HOLD_TICKS = 11;
    /** Wie lange die Bruchstuecke danach unterwegs sind. */
    public static final int LOSS_SHATTER_TICKS = 32;
    public static final int LOSS_TOTAL_TICKS = LOSS_HOLD_TICKS + LOSS_SHATTER_TICKS;

    private static int hearts = PlayerState.DEFAULT_HEARTS;
    private static String bountyTargetName = "";
    private static boolean bountyResolved;
    private static int playtimeRemainingSeconds = PlayerState.DAILY_PLAYTIME_SECONDS;
    private static Phase phase = Phase.AUFBAU;

    /**
     * Eine Uhr fuer HUD und Overlay gemeinsam. Getrennte Zaehler koennten auseinanderlaufen,
     * und der ganze Effekt lebt davon, dass beide im selben Moment zerspringen.
     */
    private static int lossTicks;

    private ClientState() {
    }

    public static void accept(StateSyncPayload payload) {
        hearts = payload.hearts();
        bountyTargetName = payload.bountyTargetName();
        bountyResolved = payload.bountyResolved();
        playtimeRemainingSeconds = payload.playtimeRemainingSeconds();
        phase = Phase.byId(payload.phaseId());
    }

    /** Startet Halten und Zerspringen. Das verlorene Herz bleibt so lange sichtbar. */
    public static void onHeartLost(int remaining) {
        hearts = remaining;
        lossTicks = LOSS_TOTAL_TICKS;
        playSound(SoundEvents.WARDEN_HEARTBEAT, 1.4f, 0.7f);
    }

    public static void tick() {
        if (lossTicks <= 0) {
            return;
        }

        lossTicks--;

        // Der Glasbruch gehoert an den Moment des Bruchs, nicht an den Anfang des Haltens.
        if (lossTicks == LOSS_SHATTER_TICKS) {
            playSound(SoundEvents.GLASS_BREAK, 1.0f, 0.5f);
        }
    }

    private static void playSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.playSound(sound, volume, pitch);
        }
    }

    public static boolean isLossRunning() {
        return lossTicks > 0;
    }

    /** Verstrichene Ticks seit Beginn, mit Teiltick fuer fluessige Bewegung. */
    public static float lossElapsedTicks(float partialTick) {
        return LOSS_TOTAL_TICKS - Math.max(0f, lossTicks - partialTick);
    }

    /** 0.0 solange das Herz noch heil steht, danach 0..1 fuer das Zerspringen. */
    public static float lossShatterProgress(float partialTick) {
        float elapsed = lossElapsedTicks(partialTick);
        return Mth.clamp((elapsed - LOSS_HOLD_TICKS) / LOSS_SHATTER_TICKS, 0f, 1f);
    }

    /** 0.0 am Anfang des Haltens, 1.0 kurz vor dem Bruch. Fuer das Anschwellen davor. */
    public static float lossHoldProgress(float partialTick) {
        return Mth.clamp(lossElapsedTicks(partialTick) / LOSS_HOLD_TICKS, 0f, 1f);
    }

    /** Beim Verlassen einer Welt aufrufen, damit nichts in die naechste Session leckt. */
    public static void reset() {
        hearts = PlayerState.DEFAULT_HEARTS;
        bountyTargetName = "";
        bountyResolved = false;
        playtimeRemainingSeconds = PlayerState.DAILY_PLAYTIME_SECONDS;
        phase = Phase.AUFBAU;
        lossTicks = 0;
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
