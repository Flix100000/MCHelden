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
    /**
     * Haelt ein laufendes Zeit-Event die Uhr gerade an?
     *
     * <p>Dann bleibt die Zahl stehen, statt weiter heruntergezaehlt zu werden — der Server
     * verbraucht ja auch keine Sekunde.
     */
    private static boolean playtimePaused;
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

    /**
     * Wie lange die Kuppel schon aufzieht, beziehungsweise schon zerbricht.
     *
     * <p>Getrennte Uhren, weil es zwei Vorgaenge sind: das Gluehen laeuft im Countdown und
     * kann abgebrochen werden, der Bruch laeuft danach und ist endgueltig. {@code -1} heisst
     * jeweils: passiert gerade nicht.
     */
    private static int safeZoneArmTicks = -1;
    private static int safeZoneShatterTicks = -1;

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

    /**
     * Die Ansage beim Ausscheiden. Sie laeuft wie ein Vanilla-Titel, wird aber selbst
     * gezeichnet — der Vanilla-Titel vergroessert fest vierfach und lief aus dem Bild.
     */
    private static int eliminationTicks;
    private static String eliminationVictim = "";
    private static String eliminationKiller = "";

    /** Combat-Timer. Der Server schickt nur Aenderungen, heruntergezaehlt wird hier. */
    private static final TimerState COMBAT = new TimerState();
    /**
     * Duell-Timer. Laeuft nie gleichzeitig mit dem Combat-Timer: im Duell gibt es zwischen
     * den beiden keinen Kampf-Timer, und der Moment, in dem beides zusammentraefe, ist
     * genau der, in dem das Duell platzt.
     */
    private static final TimerState DUEL = new TimerState();

    /** Der Duellgegner. Der Glow haengt allein an ihm. */
    @Nullable
    private static UUID duelOpponentId;

    private static int pearlsLeft;
    private static int cobwebsLeft;

    /** Ein einzelner nachgereichter Ton, fuer zweitoenige Figuren. */
    private static SoundEvent pendingSound;
    private static float pendingVolume;
    private static float pendingPitch;
    private static int pendingDelay;

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
        playtimeRemainingSeconds = payload.playtime().remainingSeconds();
        playtimePaused = payload.playtime().paused();
        playtimeTicks = 0;
        phase = Phase.byId(payload.phaseId());
        wallUp = payload.wallUp();
        // Safezone- und Wandrenderer rechnen gegen die Arenamitte, und die kennt der Client
        // nur von hier.
        net.bananemdnsa.mchelden.world.ArenaCenter.setClient(
                payload.arena().centerX(), payload.arena().centerZ());
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
        // Haelt ein Event die Uhr an, verbraucht der Server keine Sekunde — der Client darf
        // dann auch nicht lokal weiterzaehlen, sonst spraenge die Zahl beim naechsten Sync
        // wieder nach oben.
        if (playtimePaused || playtimeRemainingSeconds <= 0 || ++playtimeTicks < 20) {
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
     * Die Kuppel zieht auf, zerspringt oder kommt wieder zur Ruhe.
     *
     * <p>Der Abbruch wird gebraucht, wenn ein Op den Phasenwechsel im Countdown
     * zuruecknimmt: sonst bliebe eine gluehende Kuppel stehen, die nie zerbricht.
     */
    public static void onSafeZoneShatter(
            net.bananemdnsa.mchelden.network.SafeZoneShatterPayload.Stage stage) {
        switch (stage) {
            case ARM -> {
                safeZoneArmTicks = 0;
                safeZoneShatterTicks = -1;
            }
            case BREAK -> {
                safeZoneArmTicks = -1;
                safeZoneShatterTicks = 0;
            }
            case CANCEL -> {
                safeZoneArmTicks = -1;
                safeZoneShatterTicks = -1;
            }
        }
    }

    /** Wie lange die Kuppel schon aufzieht, oder -1. */
    public static int safeZoneArmTicks() {
        return safeZoneArmTicks;
    }

    /** Wie lange die Kuppel schon zerbricht, oder -1. */
    public static int safeZoneShatterTicks() {
        return safeZoneShatterTicks;
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

    /** Startet die Ansage, dass jemand ausgeschieden ist. */
    public static void onElimination(String victim, String killer) {
        eliminationVictim = victim;
        eliminationKiller = killer;
        eliminationTicks =
                net.bananemdnsa.mchelden.client.hud.EliminationAnnouncement.TOTAL_TICKS;
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
        cobwebsLeft = payload.cobwebsLeft();
        COMBAT.accept(payload.remainingTicks());
        updateSafeZoneLock();
    }

    /**
     * Uebernimmt den Duell-Stand und den Gegner.
     *
     * <p>Der Gegner steht mit im Paket, weil der Glow an ihm haengt: der Client laesst genau
     * diesen einen Spieler fuer sich leuchten.
     */
    public static void onDuel(net.bananemdnsa.mchelden.network.DuelSyncPayload payload) {
        duelOpponentId = payload.opponent().orElse(null);
        DUEL.accept(payload.remainingTicks());
        updateSafeZoneLock();
    }

    /**
     * Haelt die Safezone-Sperre auf dem Stand des Servers.
     *
     * <p>Der Client zaehlt beide Timer selbst herunter, und die Sperre laeuft auf beiden
     * Seiten — sie muss denselben Stand kennen, sonst steht der Spieler vor einer Kuppel,
     * durch die er laut Server hindurchdarf.
     */
    private static void updateSafeZoneLock() {
        net.bananemdnsa.mchelden.world.SafeZone.setClientInCombat(isFighting());
    }

    /** Ist das mein Duellgegner? Der Glow fragt das pro Entitaet und Bild. */
    public static boolean isDuelOpponent(net.minecraft.world.entity.Entity entity) {
        return duelOpponentId != null && duelOpponentId.equals(entity.getUUID());
    }

    public static TimerState combat() {
        return COMBAT;
    }

    public static TimerState duel() {
        return DUEL;
    }

    /**
     * Kampf oder Duell — fuer alles, was zwischen beiden nicht unterscheidet: die
     * Kontingent-Anzeige und die Safezone-Sperre.
     */
    public static boolean isFighting() {
        return COMBAT.isRunning() || DUEL.isRunning();
    }

    public static int getPearlsLeft() {
        return pearlsLeft;
    }

    public static int getCobwebsLeft() {
        return cobwebsLeft;
    }

    public static void tick() {
        if (pendingDelay > 0 && --pendingDelay == 0 && pendingSound != null) {
            play(pendingSound, pendingVolume, pendingPitch);
            pendingSound = null;
        }

        COMBAT.tick();
        DUEL.tick();

        // Der Glow haengt am laufenden Timer: laeuft der aus, geht der Gegner mit, ohne auf
        // das Paket vom Server zu warten. Sonst leuchtete nach einem Debug-Duell, hinter dem
        // gar kein Duell steht, eine Kuh bis zum Weltwechsel weiter.
        if (duelOpponentId != null && !DUEL.isRunning()) {
            duelOpponentId = null;
        }

        tickPlaytime();

        // Gedeckelt: nach dem Durchbruch zeichnet der Renderer ohnehin nichts mehr, und ein
        // Zaehler, der stundenlang weiterlaeuft, hilft niemandem.
        if (wallDropTicks >= 0 && wallDropTicks < WALL_DROP_MAX_TICKS) {
            wallDropTicks++;
        }

        if (safeZoneArmTicks >= 0) {
            safeZoneArmTicks++;
        }

        if (safeZoneShatterTicks >= 0 && ++safeZoneShatterTicks
                > net.bananemdnsa.mchelden.client.render.SafeZoneShatter.TOTAL_TICKS) {
            safeZoneShatterTicks = -1;
        }

        // Die Kollision liest denselben Wert wie der Renderer, sonst steht man vor einer
        // abgesunkenen Wand, durch die man trotzdem nicht hindurchkommt.
        DividerWall.setClientEdge(wallEdge(0f));

        updateSafeZoneLock();

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
        if (eliminationTicks > 0) {
            eliminationTicks--;
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

    static void playDelayed(SoundEvent sound, float volume, float pitch, int delayTicks) {
        pendingSound = sound;
        pendingVolume = volume;
        pendingPitch = pitch;
        pendingDelay = delayTicks;
    }

    static void play(SoundEvent sound, float volume, float pitch) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.playSound(sound, volume, pitch);
        }
    }

    public static boolean isEliminationRunning() {
        return eliminationTicks > 0;
    }

    /** Verbleibende Ticks der Ansage, mit Teiltick fuer eine gleichmaessige Blende. */
    public static float eliminationTicksLeft(float partialTick) {
        return Math.max(0f, eliminationTicks - partialTick);
    }

    public static String eliminationVictim() {
        return eliminationVictim;
    }

    public static String eliminationKiller() {
        return eliminationKiller;
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
        playtimePaused = false;
        playtimeTicks = 0;
        phase = Phase.AUFBAU;
        wallUp = true;
        net.bananemdnsa.mchelden.world.ArenaCenter.setClient(0.0, 0.0);
        wallDropTicks = -1;
        safeZoneArmTicks = -1;
        safeZoneShatterTicks = -1;
        DividerWall.setClientEdge(Double.MAX_VALUE);
        lossTicks = 0;
        eliminationTicks = 0;
        eliminationVictim = "";
        eliminationKiller = "";
        COMBAT.reset();
        DUEL.reset();
        duelOpponentId = null;
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

    /** Steht die Uhr gerade still, weil ein Zeit-Event laeuft? */
    public static boolean isPlaytimePaused() {
        return playtimePaused;
    }

    public static boolean isWallUp() {
        return wallUp;
    }

    public static Phase getPhase() {
        return phase;
    }
}
