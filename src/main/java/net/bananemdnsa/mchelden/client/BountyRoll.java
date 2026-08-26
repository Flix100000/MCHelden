package net.bananemdnsa.mchelden.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.bananemdnsa.mchelden.bounty.BountyRollTiming;
import net.bananemdnsa.mchelden.network.BountyRollPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Die Uhr und die Tonspur des Bounty-Gluecksrads. Rein clientseitig.
 *
 * <p>Der Roll passiert im ganzen Projekt genau einmal — deswegen bekommt er elf Sekunden
 * und sechs Zuege statt einer kurzen Drehung. Das dramatische Stueck ist der
 * <em>Fast-Stopp</em>: der Streifen bleibt auf dem Nachbarn des richtigen Kopfes stehen,
 * haelt dort still, und kriecht dann noch eine Kachel weiter.
 *
 * <p>Eigene Klasse, weil das sonst {@link ClientState} zur Sammelstelle machen wuerde. Hier
 * liegt nur, was mit dem Lauf zu tun hat; der gesyncte Zustand bleibt drueben. Die Laengen
 * der Zuege stehen in {@link BountyRollTiming} — der Server braucht dieselben Zahlen.
 */
public final class BountyRoll {
    /** Genug Kacheln, dass rechts vom Zeiger nie das Ende des Streifens sichtbar wird. */
    private static final int REEL_LENGTH = BountyRollTiming.LANDING_INDEX + 24;
    /** Weniger verschiedene Koepfe als das, und der Lauf sieht aus wie ein Standbild. */
    private static final int MIN_POOL = 8;

    /** Anteil des Laufs, in dem der Streifen anzieht. */
    private static final float RAMP = BountyRollTiming.OPEN_TICKS / (float) BountyRollTiming.RUN_LENGTH;
    /** Ab hier wird er wieder langsamer. */
    private static final float CRUISE = (BountyRollTiming.OPEN_TICKS + BountyRollTiming.SPIN_TICKS)
            / (float) BountyRollTiming.RUN_LENGTH;

    /**
     * Zurueckgelegter Weg je Tick des Laufs, auf 0..1 normiert.
     *
     * <p>Aus einem Geschwindigkeitsverlauf aufsummiert statt aus einer Weg-Formel geraten:
     * anziehen, Vollgas, ausrollen sind drei Abschnitte, und nur ueber die Geschwindigkeit
     * gehen sie ohne Sprung ineinander ueber. Der Klickton haengt an denselben Zahlen, er
     * wird dadurch von allein langsamer.
     */
    private static final float[] DISTANCE = buildDistanceTable();

    /** Wie stark das Band beim Einrasten ruckt, in Pixeln. */
    private static final float SHAKE_STRENGTH = 5f;
    /** Fest verdrahtet statt zufaellig, damit jeder Roll gleich aussieht. */
    private static final float[] SHAKE_X = {1f, -0.7f, 0.45f, -0.3f, 0.15f, -0.08f};
    private static final float[] SHAKE_Y = {-0.6f, 0.8f, -0.35f, 0.2f, -0.1f, 0.05f};

    /** Herzschlag unter dem Ausrollen: je langsamer der Streifen, desto traeger der Puls. */
    private static final int HEARTBEAT_MIN_GAP = 13;
    private static final int HEARTBEAT_MAX_GAP = 30;

    /** Die Kacheln des Streifens. {@code null} steht fuer die Fragezeichen-Kachel. */
    private static final List<UUID> REEL = new ArrayList<>();

    /**
     * Name des ausgelosten Ziels, wie er unter dem Band steht.
     *
     * <p>Kommt aus dem Roll-Paket und nicht aus dem gesyncten Zustand: {@code /helden debug
     * bounty} spielt den Lauf ab, ohne am Zustand etwas zu aendern.
     */
    private static String targetName = "";

    private static int ticks;
    /** Wie viele Kacheln schon am Zeiger vorbei sind. Haengt den Klickton an die Bewegung. */
    private static int clicks;
    /** Ticks bis zum naechsten Herzschlag. */
    private static int heartbeat;

    private BountyRoll() {
    }

    public static void start(BountyRollPayload payload) {
        buildReel(payload.targetId().orElse(null));
        targetName = payload.targetName();
        ticks = BountyRollTiming.TOTAL_TICKS;
        clicks = 0;
        heartbeat = 0;

        // Nur die Ansage. Der Streifen bekommt seinen eigenen Auftakt, wenn sie weg ist.
        play(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1.0f, 0.5f);
    }

    public static void reset() {
        REEL.clear();
        targetName = "";
        ticks = 0;
        clicks = 0;
        heartbeat = 0;
    }

    public static boolean isRunning() {
        return ticks > 0;
    }

    public static void tick() {
        if (ticks <= 0) {
            return;
        }

        ticks--;
        int elapsed = BountyRollTiming.TOTAL_TICKS - ticks;

        if (elapsed == BountyRollTiming.TITLE_END) {
            // Die Ansage ist weg, das Band geht auf.
            play(SoundEvents.NOTE_BLOCK_BIT.value(), 0.6f, 0.6f);
            return;
        }

        if (elapsed == BountyRollTiming.CREEP_END) {
            // Der Fast-Stopp loest sich: ein harter Klack, deutlich anders als die Klicks davor.
            // Der Klack ersetzt den Klick dieser Kachel, deswegen wird sie hier abgehakt.
            clicks = BountyRollTiming.LANDING_INDEX;
            play(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.9f, 0.5f);
            play(SoundEvents.ANVIL_LAND, 0.55f, 0.6f);
            play(SoundEvents.NOTE_BLOCK_BELL.value(), 1.1f, 0.5f);
            play(SoundEvents.TRIDENT_THUNDER.value(), 0.5f, 0.8f);
            return;
        }

        if (elapsed == BountyRollTiming.CREEP_END + 5) {
            play(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.7f, 1.8f);
            return;
        }

        tickClicks();
        tickHeartbeat(elapsed);
    }

    /** Ein Klick, sobald ein Kopf den Zeiger passiert hat. */
    private static void tickClicks() {
        int passed = (int) tiles(0f);
        if (passed <= clicks) {
            return;
        }

        clicks = passed;
        float progress = passed / (float) BountyRollTiming.LANDING_INDEX;
        play(SoundEvents.NOTE_BLOCK_HAT.value(), 0.4f, 1.0f + progress);
    }

    /**
     * Der Herzschlag setzt mit dem Ausrollen ein und wird mit dem Streifen traeger.
     *
     * <p>Waehrend des Fast-Stopps ist er das Einzige, was noch zu hoeren ist — genau das
     * macht die Stille dort aus.
     */
    private static void tickHeartbeat(int elapsed) {
        int slowFrom = BountyRollTiming.TITLE_END + BountyRollTiming.OPEN_TICKS + BountyRollTiming.SPIN_TICKS;
        if (elapsed < slowFrom || elapsed > BountyRollTiming.CREEP_END) {
            return;
        }

        if (--heartbeat > 0) {
            return;
        }

        float slowness = 1f - speed(0f);
        heartbeat = Math.round(Mth.lerp(slowness, HEARTBEAT_MIN_GAP, HEARTBEAT_MAX_GAP));
        play(SoundEvents.WARDEN_HEARTBEAT, 0.9f, 0.7f);
    }

    /** Verstrichene Ticks seit Beginn, mit Teiltick fuer fluessige Bewegung. */
    public static float elapsed(float partialTick) {
        return BountyRollTiming.TOTAL_TICKS - Math.max(0f, ticks - partialTick);
    }

    /**
     * Wie weit der Streifen gelaufen ist, in Kacheln.
     *
     * <p>Drei Abschnitte: der Lauf bis zur vorletzten Kachel, der vorgetaeuschte Stillstand,
     * und das Weiterkriechen um genau eine Kachel.
     */
    public static float tiles(float partialTick) {
        float now = elapsed(partialTick);

        if (now <= BountyRollTiming.TITLE_END) {
            return 0f;
        }
        if (now <= BountyRollTiming.RUN_END) {
            return distance(now - BountyRollTiming.TITLE_END) * (BountyRollTiming.LANDING_INDEX - 1);
        }
        if (now <= BountyRollTiming.HOLD_END) {
            return BountyRollTiming.LANDING_INDEX - 1;
        }
        if (now >= BountyRollTiming.CREEP_END) {
            return BountyRollTiming.LANDING_INDEX;
        }

        float creep = (now - BountyRollTiming.HOLD_END) / BountyRollTiming.CREEP_TICKS;
        return BountyRollTiming.LANDING_INDEX - 1 + creep * creep * (3f - 2f * creep);
    }

    /**
     * Das aktuelle Tempo, 0.0 im Stillstand und 1.0 bei Vollgas.
     *
     * <p>Das Bild haengt daran: der Spotlight zieht sich zu, je langsamer es wird, und die
     * Kacheln wachsen. Beides erzaehlt dasselbe wie die Klicks, nur fuers Auge.
     */
    public static float speed(float partialTick) {
        float now = elapsed(partialTick);

        // Nach dem Lauf steht der Streifen. Auch das Kriechen zaehlt als Stillstand: der
        // Spotlight soll waehrend des Fast-Stopps zu bleiben, nicht wieder aufgehen.
        if (now <= BountyRollTiming.TITLE_END || now > BountyRollTiming.RUN_END) {
            return 0f;
        }
        return Mth.clamp(shape((now - BountyRollTiming.TITLE_END) / BountyRollTiming.RUN_LENGTH), 0f, 1f);
    }

    /** Der Ruck beim Einrasten, abklingend. Zwei Pixelwerte, x und y. */
    public static float shakeX(float partialTick) {
        return shake(partialTick, SHAKE_X);
    }

    public static float shakeY(float partialTick) {
        return shake(partialTick, SHAKE_Y);
    }

    private static float shake(float partialTick, float[] pattern) {
        float since = elapsed(partialTick) - BountyRollTiming.CREEP_END;
        if (since < 0f || since >= pattern.length) {
            return 0f;
        }

        int step = (int) since;
        float decay = 1f - since / pattern.length;
        return pattern[step] * SHAKE_STRENGTH * decay;
    }

    public static String getTargetName() {
        return targetName;
    }

    /**
     * Die Kachel an dieser Stelle des Streifens, oder {@code null} fuer das Fragezeichen.
     *
     * <p>Der Streifen ist ein Ring: Stellen links vom Anfang greifen hinten wieder hinein.
     * Ohne das waere das Band am Anfang des Laufs zur Haelfte leer.
     */
    @Nullable
    public static UUID reelTile(int index) {
        return REEL.isEmpty() ? null : REEL.get(Math.floorMod(index, REEL.size()));
    }

    /**
     * Fuellt den Streifen aus der Spielerliste.
     *
     * <p>Sind zu wenige Leute online — im Dev-Setup der Regelfall —, wird mit erfundenen
     * UUIDs aufgefuellt. Die fallen auf die Vanilla-Standardskins zurueck, und der Lauf
     * bewegt sich sichtbar, statt dieselbe Kachel vierundsechzigmal zu zeigen.
     *
     * <p>Der Nachbar der Landekachel darf nicht derselbe Kopf sein: auf ihm bleibt der
     * Fast-Stopp stehen, und der lebt davon, dass man den Unterschied sieht.
     */
    private static void buildReel(@Nullable UUID target) {
        List<UUID> pool = new ArrayList<>();
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            for (PlayerInfo info : connection.getOnlinePlayers()) {
                pool.add(info.getProfile().getId());
            }
        }
        while (pool.size() < MIN_POOL) {
            pool.add(UUID.randomUUID());
        }

        RandomSource random = RandomSource.create();
        REEL.clear();
        for (int index = 0; index < REEL_LENGTH; index++) {
            REEL.add(pool.get(random.nextInt(pool.size())));
        }
        REEL.set(BountyRollTiming.LANDING_INDEX, target);

        UUID neighbour = REEL.get(BountyRollTiming.LANDING_INDEX - 1);
        if (Objects.equals(neighbour, target)) {
            REEL.set(BountyRollTiming.LANDING_INDEX - 1, pool.get((pool.indexOf(neighbour) + 1) % pool.size()));
        }
    }

    /** Der zurueckgelegte Weg nach {@code now} Ticks, 0..1, zwischen den Stuetzstellen gemittelt. */
    private static float distance(float sinceTitle) {
        float clamped = Mth.clamp(sinceTitle, 0f, BountyRollTiming.RUN_LENGTH);
        int step = (int) clamped;
        if (step >= BountyRollTiming.RUN_LENGTH) {
            return 1f;
        }
        return Mth.lerp(clamped - step, DISTANCE[step], DISTANCE[step + 1]);
    }

    /**
     * Der Geschwindigkeitsverlauf des Laufs: anziehen, Vollgas, ausrollen mit langem Schwanz.
     *
     * @param position Anteil des Laufs, 0..1
     */
    private static float shape(float position) {
        if (position < RAMP) {
            float ramp = position / RAMP;
            return ramp * ramp;
        }
        if (position < CRUISE) {
            return 1f;
        }

        float tail = (position - CRUISE) / (1f - CRUISE);
        return (1f - tail) * (1f - tail) * (1f - tail);
    }

    /** Summiert den Geschwindigkeitsverlauf auf und normiert ihn auf eine Gesamtstrecke von 1. */
    private static float[] buildDistanceTable() {
        float[] table = new float[BountyRollTiming.RUN_LENGTH + 1];
        float sum = 0f;
        for (int step = 1; step <= BountyRollTiming.RUN_LENGTH; step++) {
            sum += shape((step - 0.5f) / BountyRollTiming.RUN_LENGTH);
            table[step] = sum;
        }
        for (int step = 0; step <= BountyRollTiming.RUN_LENGTH; step++) {
            table[step] /= sum;
        }
        return table;
    }

    private static void play(SoundEvent sound, float volume, float pitch) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.playSound(sound, volume, pitch);
        }
    }
}
