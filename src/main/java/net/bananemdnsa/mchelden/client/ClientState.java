package net.bananemdnsa.mchelden.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.bananemdnsa.mchelden.network.BountyRollPayload;
import net.bananemdnsa.mchelden.network.StateSyncPayload;
import net.bananemdnsa.mchelden.playtime.PlaytimeTracker;
import net.bananemdnsa.mchelden.state.Phase;
import net.bananemdnsa.mchelden.state.PlayerState;
import net.bananemdnsa.mchelden.world.DividerWall;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

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
    @Nullable
    private static UUID bountyTargetId;
    private static boolean bountyResolved;
    private static boolean bountyTargetEliminated;
    /**
     * Verbleibende Spielzeit, oder {@link PlaytimeTracker#NO_LIMIT}.
     *
     * <p>Der Server schickt nur Aenderungen, heruntergezaehlt wird hier — genau wie beim
     * Combat-Timer. Ein Paket pro Sekunde und Spieler waere fuer eine Anzeige zu viel.
     */
    private static int playtimeRemainingSeconds = PlaytimeTracker.NO_LIMIT;
    /** Der Client tickt zwanzigmal pro Sekunde, die Uhr laeuft aber in Sekunden. */
    private static int playtimeTicks;
    private static Phase phase = Phase.AUFBAU;
    /** Steht die Trennwand? Der Renderer haengt allein daran. */
    private static boolean wallUp = true;
    /**
     * Wie lange der Bruch schon laeuft.
     *
     * <p>Die Wand faellt nicht weg, sie bricht — von der Mitte nach aussen, hinter der
     * Partikelwelle her. Ohne diese Uhr wuerde sie im letzten Moment einfach verschwinden.
     */
    private static int wallDropTicks = -1;
    /** Ab hier ist jede erreichbare Welt durchgebrochen. */
    private static final int WALL_DROP_MAX_TICKS = 2000;

    /** Wie lange der Rahmen des Kastens aufleuchtet, wenn ein Ziel ankommt. */
    public static final int BOUNTY_APPEAR_TICKS = 10;
    /** Wie lange der Balken ueber einem ausgeschiedenen Ziel einfaehrt. */
    public static final int BOUNTY_GONE_TICKS = 8;
    /** Wie lange der Kasten zusammenfaehrt, wenn das Bounty aufgeloest ist. */
    public static final int BOUNTY_CLOSE_TICKS = 10;

    private static int bountyAppearTicks;
    private static int bountyGoneTicks;
    private static int bountyCloseTicks;

    /**
     * Eine Uhr fuer HUD und Overlay gemeinsam. Getrennte Zaehler koennten auseinanderlaufen,
     * und der ganze Effekt lebt davon, dass beide im selben Moment zerspringen.
     */
    private static int lossTicks;

    /** Combat-Timer. Der Server schickt nur Aenderungen, heruntergezaehlt wird hier. */
    private static int combatTicks;
    /** Laeuft kurz nach jedem Treffer, damit der Balken sichtbar aufleuchtet. */
    private static int combatFlashTicks;
    private static int combatEnterTicks;
    private static int combatExitTicks;

    private static int pearlsLeft;
    private static int cobwebsLeft;

    /** Ein einzelner nachgereichter Ton, fuer zweitoenige Figuren. */
    private static SoundEvent pendingSound;
    private static float pendingVolume;
    private static float pendingPitch;
    private static int pendingDelay;

    /** Dauer des Aufleuchtens nach einem Treffer. */
    public static final int COMBAT_FLASH_TICKS = 7;
    /** Wie lange der Balken beim Kampfbeginn einschwebt. */
    public static final int COMBAT_ENTER_TICKS = 6;
    /** Wie lange er nach Kampfende noch nachleuchtet und zusammenfaehrt. */
    public static final int COMBAT_EXIT_TICKS = 12;
    /** Ab wann der Countdown tickt und der Balken pulsiert. */
    public static final int COMBAT_WARNING_TICKS = 3 * 20;

    private ClientState() {
    }

    public static void accept(StateSyncPayload payload) {
        hearts = payload.hearts();

        UUID hadTarget = bountyTargetId;
        boolean wasGone = bountyTargetEliminated;

        bountyTargetName = payload.bounty().targetName();
        bountyTargetId = payload.bounty().targetId().orElse(null);
        bountyResolved = payload.bounty().resolved();
        bountyTargetEliminated = payload.bounty().targetEliminated();
        playtimeRemainingSeconds = payload.playtimeRemainingSeconds();
        playtimeTicks = 0;
        phase = Phase.byId(payload.phaseId());
        wallUp = payload.wallUp();
        // Die Kollision laeuft auch auf dem Client — die muss den Stand ebenfalls kennen.
        net.bananemdnsa.mchelden.world.DividerWall.setClientWallUp(wallUp);

        // Ob die Safezone gilt, folgt aus der Phase — mehr braucht ein Zylinder nicht.
        net.bananemdnsa.mchelden.world.SafeZone.setClientActive(phase != Phase.FINAL_WAR);



        noteBountyChange(hadTarget, wasGone);
    }

    /**
     * Gibt den Aenderungen am Bounty ihren Moment im HUD.
     *
     * <p>Ein Kasten, der zwischen zwei Frames einfach einen anderen Inhalt hat, wird
     * uebersehen. Ankommen, Entwertet-Werden und Verschwinden bekommen deswegen je eine
     * kurze Bewegung — kurz genug, dass sie nicht im Weg ist.
     */
    private static void noteBountyChange(@Nullable UUID hadTarget, boolean wasGone) {
        // Beim Roll kommt der Zustand elf Sekunden vor dem Kopf an. Das Aufleuchten haengt
        // deswegen am Ende des Laufs, nicht am Paket.
        if (bountyTargetId != null && !bountyTargetId.equals(hadTarget) && !BountyRoll.isRunning()) {
            bountyAppearTicks = BOUNTY_APPEAR_TICKS;
        }

        if (bountyTargetEliminated && !wasGone) {
            bountyGoneTicks = BOUNTY_GONE_TICKS;
            play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f, 0.5f);
        }

        if (hadTarget != null && bountyTargetId == null && bountyResolved) {
            bountyCloseTicks = BOUNTY_CLOSE_TICKS;
        }
    }

    /** Der ausgeloste Kopf ist in seinem Kasten angekommen. Ruft {@link BountyRoll} auf. */
    static void onRollLanded() {
        if (bountyTargetId != null) {
            bountyAppearTicks = BOUNTY_APPEAR_TICKS;
        }
    }

    /**
     * Startet das Gluecksrad.
     *
     * <p>Der Zustand vom Server kommt im selben Moment an, das HUD zeigt aber weiter das
     * Fragezeichen, solange der Lauf laeuft — sonst stuende das Ergebnis oben links, bevor
     * der Streifen es preisgibt.
     */
    public static void onBountyRoll(BountyRollPayload payload) {
        BountyRoll.start(payload);
    }

    /** Laesst die Uhr oben rechts laufen, ohne dass der Server jede Sekunde etwas schickt. */
    private static void tickPlaytime() {
        if (playtimeRemainingSeconds <= 0 || ++playtimeTicks < 20) {
            return;
        }

        playtimeTicks = 0;
        playtimeRemainingSeconds--;
    }

    /** Die Wand beginnt aufzubrechen — oder der Vorgang wird abgebrochen. */
    public static void onWallDrop(boolean dropping) {
        wallDropTicks = dropping ? 0 : -1;
        DividerWall.setClientEdge(Double.MAX_VALUE);
    }

    /**
     * Wo die Oberkante der sinkenden Wand steht, in Welt-Y.
     *
     * <p>{@link Double#MAX_VALUE}, solange sie nicht sinkt — dann reicht die Wand bis oben.
     */
    public static double wallEdge(float partialTick) {
        return wallDropTicks < 0
                ? Double.MAX_VALUE
                : DividerWall.edgeAt(wallDropTicks + partialTick);
    }

    /** Startet Halten und Zerspringen. Das verlorene Herz bleibt so lange sichtbar. */
    public static void onHeartLost(int remaining) {
        hearts = remaining;
        lossTicks = LOSS_TOTAL_TICKS;
    }

    /**
     * Uebernimmt den Stand vom Server. 0 bedeutet: Kampf vorbei.
     *
     * <p>Steigt der Wert, war es ein Treffer — der Balken leuchtet dann kurz auf. Faellt er
     * auf null, ist der Kampf ausgelaufen und man darf wieder an Kisten und in die Safezone.
     * Genau das braucht einen Ton, weil man es nach drei Minuten sonst nicht mitbekommt.
     */
    public static void onCombat(net.bananemdnsa.mchelden.network.CombatSyncPayload payload) {
        pearlsLeft = payload.pearlsLeft();
        net.bananemdnsa.mchelden.world.SafeZone.setClientInCombat(payload.remainingTicks() > 0);
        cobwebsLeft = payload.cobwebsLeft();

        int remainingTicks = payload.remainingTicks();
        if (remainingTicks > combatTicks) {
            if (combatTicks == 0) {
                beginCombat();
            }
            combatFlashTicks = COMBAT_FLASH_TICKS;
        } else if (remainingTicks == 0 && combatTicks > 0) {
            // Erzwungenes Raeumen, etwa per Command oder beim Tod.
            endCombat();
        }
        combatTicks = remainingTicks;
    }

    /** Tiefer Schlag, kurz darauf ein zweiter Ton — der Eintritt bekommt einen Moment. */
    private static void beginCombat() {
        combatEnterTicks = COMBAT_ENTER_TICKS;
        combatExitTicks = 0;
        play(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.9f, 0.6f);
        playDelayed(SoundEvents.NOTE_BLOCK_BIT.value(), 0.5f, 0.8f, 3);
    }

    /** Zwei aufsteigende Toene: die Anspannung loest sich, man darf wieder an die Kisten. */
    private static void endCombat() {
        combatExitTicks = COMBAT_EXIT_TICKS;
        play(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.7f, 1.3f);
        playDelayed(SoundEvents.NOTE_BLOCK_PLING.value(), 0.6f, 1.9f, 4);
    }

    /** Balken sichtbar? Nach Kampfende noch waehrend des Ausblendens. */
    public static boolean isCombatVisible() {
        return combatTicks > 0 || combatExitTicks > 0;
    }

    /** 0.0 beim Einschweben, 1.0 wenn der Balken steht. */
    public static float combatEnter(float partialTick) {
        return 1f - Mth.clamp(Math.max(0f, combatEnterTicks - partialTick) / COMBAT_ENTER_TICKS, 0f, 1f);
    }

    /** 1.0 direkt nach Kampfende, 0.0 wenn der Balken weg ist. */
    public static float combatExit(float partialTick) {
        return Mth.clamp(Math.max(0f, combatExitTicks - partialTick) / COMBAT_EXIT_TICKS, 0f, 1f);
    }

    /** 1.0 direkt nach einem Treffer, 0.0 wenn das Aufleuchten vorbei ist. */
    public static float combatFlash(float partialTick) {
        return Mth.clamp(Math.max(0f, combatFlashTicks - partialTick) / COMBAT_FLASH_TICKS, 0f, 1f);
    }

    public static boolean isInCombat() {
        return combatTicks > 0;
    }

    public static int getPearlsLeft() {
        return pearlsLeft;
    }

    public static int getCobwebsLeft() {
        return cobwebsLeft;
    }

    public static int getCombatTicks() {
        return combatTicks;
    }

    public static void tick() {
        if (pendingDelay > 0 && --pendingDelay == 0 && pendingSound != null) {
            play(pendingSound, pendingVolume, pendingPitch);
            pendingSound = null;
        }

        if (combatTicks > 0) {
            combatTicks--;

            // Der Client zaehlt selbst herunter und erreicht die Null oft einen Tick vor dem
            // Paket vom Server. Das Kampfende haengt deswegen hier und nicht am Paket —
            // sonst wird die Ausblendung genau beim regulaeren Ablaufen verschluckt.
            if (combatTicks == 0) {
                endCombat();
            } else if (combatTicks <= COMBAT_WARNING_TICKS && combatTicks % 20 == 0) {
                // Ticken in den letzten Sekunden. Ein Ton erreicht einen auch dann, wenn man
                // gerade woanders hinschaut — anders als jedes Blinken.
                float step = (COMBAT_WARNING_TICKS - combatTicks) / (float) COMBAT_WARNING_TICKS;
                play(SoundEvents.NOTE_BLOCK_PLING.value(), 0.7f, 1.2f + step * 0.5f);
            }
        }
        if (combatFlashTicks > 0) {
            combatFlashTicks--;
        }
        if (combatEnterTicks > 0) {
            combatEnterTicks--;
        }
        if (combatExitTicks > 0) {
            combatExitTicks--;
        }

        tickPlaytime();

        // Gedeckelt: nach dem Durchbruch zeichnet der Renderer ohnehin nichts mehr, und ein
        // Zaehler, der stundenlang weiterlaeuft, hilft niemandem.
        if (wallDropTicks >= 0 && wallDropTicks < WALL_DROP_MAX_TICKS) {
            wallDropTicks++;
        }

        // Die Kollision liest denselben Wert wie der Renderer, sonst steht man vor einer
        // abgesunkenen Wand, durch die man trotzdem nicht hindurchkommt.
        DividerWall.setClientEdge(wallEdge(0f));

        // Der Client zaehlt den Combat-Timer selbst herunter — die Safezone-Sperre haengt
        // daran und muss denselben Stand kennen.
        net.bananemdnsa.mchelden.world.SafeZone.setClientInCombat(combatTicks > 0);

        boolean wasRolling = BountyRoll.isRunning();
        BountyRoll.tick();
        if (wasRolling && !BountyRoll.isRunning()) {
            onRollLanded();
        }

        if (bountyAppearTicks > 0) {
            bountyAppearTicks--;
        }
        if (bountyGoneTicks > 0) {
            bountyGoneTicks--;
        }
        if (bountyCloseTicks > 0) {
            bountyCloseTicks--;
        }

        if (lossTicks <= 0) {
            return;
        }

        lossTicks--;
        playSoundtrack(LOSS_TOTAL_TICKS - lossTicks);
    }

    /**
     * Die Tonspur des Herzverlusts.
     *
     * <p>Ein einzelner Klang traegt so einen Moment nicht. Der Bruch besteht deswegen aus drei
     * gleichzeitigen Schichten — Splittern, Kristall und Wucht —, davor zwei sich beschleunigende
     * Herzschlaege und danach ein Nachklang der herabrieselnden Scherben.
     */
    private static void playSoundtrack(int elapsed) {
        switch (elapsed) {
            case 1 -> play(SoundEvents.WARDEN_HEARTBEAT, 1.6f, 0.5f);
            case 6 -> play(SoundEvents.WARDEN_HEARTBEAT, 1.8f, 0.65f);
            case LOSS_HOLD_TICKS -> {
                play(SoundEvents.GLASS_BREAK, 1.0f, 0.5f);
                play(SoundEvents.AMETHYST_BLOCK_BREAK, 1.3f, 0.6f);
                play(SoundEvents.TRIDENT_THUNDER.value(), 0.6f, 0.7f);
            }
            case LOSS_HOLD_TICKS + 3 -> play(SoundEvents.AMETHYST_CLUSTER_BREAK, 0.8f, 1.4f);
            case LOSS_HOLD_TICKS + 9 -> play(SoundEvents.AMETHYST_CLUSTER_BREAK, 0.5f, 1.7f);
            default -> {
            }
        }
    }

    private static void playDelayed(SoundEvent sound, float volume, float pitch, int delayTicks) {
        pendingSound = sound;
        pendingVolume = volume;
        pendingPitch = pitch;
        pendingDelay = delayTicks;
    }

    private static void play(SoundEvent sound, float volume, float pitch) {
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
        bountyTargetId = null;
        bountyResolved = false;
        bountyTargetEliminated = false;
        BountyRoll.reset();
        net.bananemdnsa.mchelden.client.hud.PlayerHead.forget();
        bountyAppearTicks = 0;
        bountyGoneTicks = 0;
        bountyCloseTicks = 0;
        playtimeRemainingSeconds = PlaytimeTracker.NO_LIMIT;
        playtimeTicks = 0;
        phase = Phase.AUFBAU;
        wallUp = true;
        wallDropTicks = -1;
        DividerWall.setClientEdge(Double.MAX_VALUE);
        lossTicks = 0;
        combatTicks = 0;
        combatFlashTicks = 0;
        combatEnterTicks = 0;
        combatExitTicks = 0;
        pendingSound = null;
        pendingDelay = 0;
        pearlsLeft = 0;
        cobwebsLeft = 0;
    }

    public static int getHearts() {
        return hearts;
    }

    public static boolean hasBounty() {
        return bountyTargetId != null;
    }

    public static String getBountyTargetName() {
        return bountyTargetName;
    }

    @Nullable
    public static UUID getBountyTargetId() {
        return bountyTargetId;
    }

    public static boolean isBountyResolved() {
        return bountyResolved;
    }

    /** Das Ziel ist ausgeschieden: der Kopf bleibt stehen, aber grau und durchgestrichen. */
    public static boolean isBountyTargetEliminated() {
        return bountyTargetEliminated;
    }

    /** Der Kasten leuchtet kurz auf, wenn ein Ziel darin ankommt. 1.0 direkt danach. */
    public static float bountyAppear(float partialTick) {
        return Mth.clamp(Math.max(0f, bountyAppearTicks - partialTick) / BOUNTY_APPEAR_TICKS, 0f, 1f);
    }

    /** Wie weit der Balken ueber einem ausgeschiedenen Ziel eingefahren ist. 1.0 wenn ganz. */
    public static float bountyGone(float partialTick) {
        if (bountyGoneTicks <= 0) {
            return bountyTargetEliminated ? 1f : 0f;
        }
        return 1f - Mth.clamp(Math.max(0f, bountyGoneTicks - partialTick) / BOUNTY_GONE_TICKS, 0f, 1f);
    }

    /** 1.0 wenn der Kasten noch offen ist, 0.0 wenn er zugefahren ist. */
    public static float bountyClose(float partialTick) {
        return Mth.clamp(Math.max(0f, bountyCloseTicks - partialTick) / BOUNTY_CLOSE_TICKS, 0f, 1f);
    }

    public static boolean isBountyClosing() {
        return bountyCloseTicks > 0;
    }

    /** Gilt fuer diesen Spieler ueberhaupt ein Limit? Ops und der Krieg kennen keins. */
    public static boolean hasPlaytimeLimit() {
        return playtimeRemainingSeconds != PlaytimeTracker.NO_LIMIT;
    }

    public static int getPlaytimeRemainingSeconds() {
        return playtimeRemainingSeconds;
    }

    public static boolean isWallUp() {
        return wallUp;
    }

    public static Phase getPhase() {
        return phase;
    }
}
