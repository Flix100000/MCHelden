package net.bananemdnsa.mchelden.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.border.BorderStatus;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Es wird ungemuetlich, je naeher man der schrumpfenden Border kommt.
 *
 * <p>Vanilla warnt nur mit einem roten Rand am Bildschirm. Das reicht fuer eine Border,
 * die irgendwo steht — nicht fuer eine, die einen zwei Stunden lang vor sich hertreibt.
 * Hier wird sie zu einer Kante, an der etwas passiert: Funken, Rauch, Einschlaege.
 *
 * <p><b>Nur waehrend sie laeuft.</b> Steht die Arena auf ihren 160, hoert es auf — sonst
 * waere das letzte Gefecht ein Dauergewitter am Rand, an das sich nach zehn Minuten alle
 * gewoehnt haetten.
 *
 * <p><b>Die Blitze sind nur zum Ansehen.</b> Ein echter Blitz zuendet den Wald an und
 * toetet den, der darunter steht. Wer an der Border stirbt, soll an der Border sterben,
 * nicht an der Deko davor.
 */
public final class BorderStorm {
    /** Ab hier wird es unruhig, in Bloecken zur naechsten Kante. */
    private static final double REACH = 40.0;

    /** Ab hier schlaegt es auch ein. */
    private static final double STRIKE_RANGE = 12.0;

    /** Wie oft ueberhaupt nachgesehen wird. Fuenfmal pro Sekunde genuegt. */
    private static final int GAP = 4;

    /** Wie wahrscheinlich ein Einschlag je Pruefung ist, direkt an der Kante. */
    private static final float STRIKE_CHANCE = 0.08f;

    /** Wie hoch und wie weit entlang der Kante die Funken streuen. */
    private static final double SPREAD_Y = 10.0;
    private static final double SPREAD_ALONG = 14.0;

    /** Wie viele Funken es hoechstens sind, direkt an der Kante. */
    private static final int MAX_SPARKS = 14;

    /** Wie selten das Grollen kommt, wenn man nah dran ist. */
    private static final float RUMBLE_CHANCE = 0.05f;

    private BorderStorm() {
    }

    /**
     * Aus dem Servertick aufrufen.
     *
     * <p><b>Haengt an der Border, nicht an der Phase.</b> Die erste Fassung verlangte
     * zusaetzlich {@code FINAL_WAR} — damit blieb ein `border shrink` ausserhalb des Final
     * War still, obwohl sich die Wand sichtbar bewegte. Was zaehlt, ist ob sie laeuft.
     */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % GAP != 0) {
            return;
        }

        ServerLevel level = server.overworld();
        WorldBorder border = level.getWorldBorder();

        // Nur waehrend sie sich bewegt. Eine stehende Border ist eine Wand, keine Drohung.
        if (border.getStatus() != BorderStatus.SHRINKING) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // Creative und Zuschauer sind bewusst nicht ausgenommen: der Effekt ist etwas
            // zum Ansehen, kein Schaden. Sie auszuschliessen hiess vor allem, dass niemand
            // ihn beim Bauen und Testen je zu sehen bekam.
            double toEdge = distanceToEdge(border, player.getX(), player.getZ());
            float heat = heat(toEdge);
            if (heat <= 0.0f) {
                continue;
            }

            spark(level, player, border, heat);

            if (strikes(toEdge) && level.random.nextFloat() < STRIKE_CHANCE * heat) {
                strike(level, player, border);
            }
        }
    }

    /**
     * Wie unruhig es an dieser Stelle ist: 0 ausser Reichweite, 1 direkt an der Kante.
     *
     * <p>Reine Rechnung, damit sie ohne Spielstart pruefbar ist — und damit
     * {@code /helden debug border} dieselbe Zahl nennen kann, nach der sich der Effekt
     * richtet. Zwei Rechnungen koennten auseinanderlaufen, und dann diagnostiziert man
     * die Diagnose.
     */
    public static float heat(double toEdge) {
        if (toEdge > REACH) {
            return 0.0f;
        }
        return (float) (1.0 - Math.max(0.0, toEdge) / REACH);
    }

    /** Ab hier schlaegt es zusaetzlich ein. */
    public static boolean strikes(double toEdge) {
        return toEdge <= STRIKE_RANGE;
    }

    /**
     * Abstand zur naechsten der vier Kanten.
     *
     * <p>Negativ, wenn jemand ausserhalb steht — der Aufrufer klemmt das auf null, denn
     * naeher als direkt daran geht es nicht.
     */
    public static double distanceToEdge(WorldBorder border, double x, double z) {
        return Math.min(
                Math.min(border.getMaxX() - x, x - border.getMinX()),
                Math.min(border.getMaxZ() - z, z - border.getMinZ()));
    }

    /**
     * Setzt Funken und Rauch auf die naechste Kante, in Hoehe des Spielers.
     *
     * <p>Einzeln an ihn geschickt und mit gesetztem Fernflag: der bequeme Weg erreicht nur
     * zweiunddreissig Bloecke um die Partikelposition, und die Kante liegt bis zu vierzig
     * Bloecke weg.
     */
    private static void spark(ServerLevel level, ServerPlayer player, WorldBorder border,
                              float heat) {
        double y = player.getY() + 1.0;
        double[] face = nearestFace(border, player.getX(), player.getZ());

        // face = {x, z, streuung in x, streuung in z}
        int count = Math.max(1, Math.round(MAX_SPARKS * heat));

        level.sendParticles(player, ParticleTypes.ELECTRIC_SPARK, true,
                face[0], y, face[1], count, face[2], SPREAD_Y, face[3], 0.06);
        level.sendParticles(player, ParticleTypes.LARGE_SMOKE, true,
                face[0], y, face[1], Math.max(1, count / 2), face[2], SPREAD_Y, face[3], 0.01);

        // Ein Grollen, das mit der Naehe lauter wird. Es erreicht einen auch dann, wenn man
        // gerade in die andere Richtung schaut.
        if (heat > 0.5f && level.random.nextFloat() < RUMBLE_CHANCE) {
            player.playNotifySound(SoundEvents.TRIDENT_THUNDER.value(), SoundSource.WEATHER,
                    0.3f + heat * 0.5f, 0.6f);
        }
    }

    /** Ein Einschlag auf der Kante selbst, in Sichtweite. Nur zum Ansehen. */
    private static void strike(ServerLevel level, ServerPlayer player, WorldBorder border) {
        double[] face = nearestFace(border, player.getX(), player.getZ());
        double offset = (level.random.nextDouble() - 0.5) * 2.0 * SPREAD_ALONG;

        // Verschoben wird entlang der Kante, also auf der Achse, die auch streut.
        double boltX = face[0] + (face[2] > 0.0 ? offset : 0.0);
        double boltZ = face[1] + (face[3] > 0.0 ? offset : 0.0);

        // getHeightmapPos laedt keine Chunks nach und liefert fuer ungeladene die
        // Weltuntergrenze — der Fehler aus Etappe 7. Hier ist er ungefaehrlich: der
        // Einschlag liegt in Sichtweite eines Spielers, der Chunk ist also geladen.
        BlockPos ground = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(boltX, player.getY(), boltZ));

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return;
        }

        bolt.moveTo(Vec3.atBottomCenterOf(ground));
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
    }

    /**
     * Der Punkt auf der naechsten Kante, und in welche Richtung sie verlaeuft.
     *
     * <p>An einer Ecke gewinnt eine der beiden — welche, ist gleichgueltig: dort sind
     * ohnehin beide in Reichweite.
     *
     * @return {@code {x, z, streuungX, streuungZ}}
     */
    private static double[] nearestFace(WorldBorder border, double x, double z) {
        double toMaxX = border.getMaxX() - x;
        double toMinX = x - border.getMinX();
        double toMaxZ = border.getMaxZ() - z;
        double toMinZ = z - border.getMinZ();
        double nearest = Math.min(Math.min(toMaxX, toMinX), Math.min(toMaxZ, toMinZ));

        if (nearest == toMaxX) {
            return new double[] {border.getMaxX(), z, 0.0, SPREAD_ALONG};
        }
        if (nearest == toMinX) {
            return new double[] {border.getMinX(), z, 0.0, SPREAD_ALONG};
        }
        if (nearest == toMaxZ) {
            return new double[] {x, border.getMaxZ(), SPREAD_ALONG, 0.0};
        }
        return new double[] {x, border.getMinZ(), SPREAD_ALONG, 0.0};
    }
}
