package net.bananemdnsa.mchelden.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;

import net.bananemdnsa.mchelden.world.SafeZone;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Der Zerfall der Safezone-Kuppel.
 *
 * <p>Die Kuppel verschwindet nicht, sie <b>zerbricht</b>: der Ring wird in ein Gitter aus
 * Scherben geschnitten, jede kippt, fliegt weg und blendet fuer sich aus.
 *
 * <p><b>Der Zufall ist wiederholbar.</b> Jede Scherbe zieht ihre Werte aus ihrer eigenen
 * Position statt aus einem Zufallsgenerator — sonst saehe jeder Client andere Truemmer,
 * und zwei Leute nebeneinander wuerden ueber verschiedene Bilder reden.
 *
 * <p><b>Der Schutz endet beim Bruch</b>, nicht wenn die letzte Scherbe liegt. Waehrend
 * dieser Animation gilt die Safezone bereits als abgeschaltet. Sonst stuende man in
 * sichtbaren Truemmern und waere trotzdem unverwundbar — derselbe Widerspruch, der beim
 * Wandfall zweimal beinahe entstanden waere.
 */
public final class SafeZoneShatter {
    /**
     * Wie lange der ganze Vorgang dauert: zehn Sekunden.
     *
     * <p>Die erste Fassung war halb so lang. Die Scherben waren weg, bevor man sie fallen
     * sehen konnte — der Bruch las sich als Aufblitzen statt als Einsturz.
     */
    public static final int TOTAL_TICKS = 200;

    /** Wie weit die spaeteste Scherbe hinterherhinkt: eine Sekunde Kaskade. */
    public static final int MAX_DELAY = 20;

    /** Wie fein der Ring geschnitten wird. Derselbe Wert wie im {@link SafeZoneRenderer}. */
    static final int SEGMENTS = 64;

    /** Wie viele Reihen uebereinander, und wie hoch jede ist. */
    static final int ROWS = 16;
    static final double ROW_HEIGHT = 8.0;

    /** Wie weit eine Scherbe hoechstens nach aussen faehrt, in Bloecken. */
    private static final double PUSH = 12.0;

    /** Wie weit die Truemmer am Ende gefallen sind, in Bloecken. */
    private static final double FALL = 70.0;

    /** Wie weit sich eine Scherbe hoechstens dreht, im Bogenmass. */
    private static final double SPIN = 5.0;

    /**
     * Ab wann eine Scherbe anfaengt zu verblassen.
     *
     * <p>Davor bleibt sie voll sichtbar. Ein Verblassen, das sofort beginnt, nimmt einem
     * genau den Teil weg, den man sehen will: das Fallen.
     */
    private static final float FADE_START = 0.6f;

    private SafeZoneShatter() {
    }

    /**
     * Wie weit eine Scherbe ist: 0 vor ihrem Einsatz, 1 am Ende.
     *
     * <p>Reine Rechnung, damit sie ohne Spielstart pruefbar ist.
     *
     * @param delay wie viele Ticks diese Scherbe spaeter losgeht
     */
    public static float progress(int ticks, float partial, int delay) {
        float elapsed = ticks + partial - delay;
        if (elapsed <= 0.0f) {
            return 0.0f;
        }
        return Mth.clamp(elapsed / (TOTAL_TICKS - delay), 0.0f, 1.0f);
    }

    /**
     * Die Deckkraft haelt und faellt dann.
     *
     * <p>Bis {@link #FADE_START} bleibt die Scherbe voll sichtbar und faellt nur; erst
     * danach loest sie sich auf. Die erste Fassung hat von Anfang an ausgeblendet — die
     * Truemmer waren durchsichtig, bevor sie irgendwo hingekommen waren.
     */
    public static float alpha(float progress) {
        if (progress <= FADE_START) {
            return 1.0f;
        }
        float left = (1.0f - progress) / (1.0f - FADE_START);
        return Mth.clamp(left * left, 0.0f, 1.0f);
    }

    /**
     * Wiederholbarer Zufall aus der Position einer Scherbe.
     *
     * <p>Ein gemischter Ganzzahl-Hash, kein Zufallsgenerator: derselbe Platz ergibt auf
     * jedem Client dieselbe Zahl, und benachbarte Plaetze ergeben trotzdem
     * unterschiedliche.
     *
     * @return ein Wert in {@code [0, 1)}
     */
    public static float noise(int segment, int row, int salt) {
        int hash = segment * 73_856_093 ^ row * 19_349_663 ^ salt * 83_492_791;
        hash ^= hash >>> 13;
        hash *= 1_274_126_177;
        hash ^= hash >>> 16;
        return (hash & 0x00FF_FFFF) / (float) 0x0100_0000;
    }

    /** Wie viele Ticks diese Scherbe spaeter losgeht. */
    static int delay(int segment, int row) {
        return (int) (noise(segment, row, 7) * MAX_DELAY);
    }

    /**
     * Schreibt alle Scherben in den Puffer.
     *
     * <p>Gerechnet wird kameranah, wie im {@link SafeZoneRenderer}: der Ring liegt um die
     * Weltachse, die Reihen um die Augenhoehe. Ueber und unter dem Band ist nichts mehr —
     * eine unendlich hohe Wand laesst sich nicht in Stuecke schneiden, und in fuenf
     * Sekunden voller fliegender Truemmer faellt das niemandem auf.
     */
    static void build(BufferBuilder buffer, Vec3 eye, int ticks, float partial) {
        double radius = SafeZone.RADIUS;
        double halfWidth = Math.PI * radius / SEGMENTS;
        double halfHeight = ROW_HEIGHT / 2.0;

        for (int segment = 0; segment < SEGMENTS; segment++) {
            double angle = 2.0 * Math.PI * (segment + 0.5) / SEGMENTS;
            double outX = Math.cos(angle);
            double outZ = Math.sin(angle);

            for (int row = 0; row < ROWS; row++) {
                float t = progress(ticks, partial, delay(segment, row));
                if (alpha(t) <= 0.0f) {
                    continue;
                }

                // Mittelpunkt der Scherbe, relativ zur Kamera.
                double push = PUSH * noise(segment, row, 1) * t;
                double drop = FALL * t * t;

                double cx = outX * (radius + push) - eye.x;
                double cz = outZ * (radius + push) - eye.z;
                double cy = (row - ROWS / 2.0 + 0.5) * ROW_HEIGHT - drop;

                // Eigene Achsen der Scherbe: entlang der Wand und nach oben, beide gekippt.
                double spin = SPIN * (noise(segment, row, 2) - 0.5) * t;
                double tilt = SPIN * (noise(segment, row, 3) - 0.5) * t;

                double ux = -outZ * halfWidth * Math.cos(spin);
                double uz = outX * halfWidth * Math.cos(spin);
                double uy = halfWidth * Math.sin(spin);

                double vx = outX * halfHeight * Math.sin(tilt);
                double vy = halfHeight * Math.cos(tilt);
                double vz = outZ * halfHeight * Math.sin(tilt);

                emit(buffer, cx, cy, cz, ux, uy, uz, vx, vy, vz, segment, row);
            }
        }
    }

    /**
     * Vier Ecken um den Mittelpunkt herum.
     *
     * <p>Die Texturkoordinaten kommen aus dem Platz der Scherbe im Ring, damit jedes Stueck
     * sein eigenes Stueck Muster behaelt: es soll aussehen, als sei die Wand zerbrochen,
     * nicht als seien neue Flaechen entstanden.
     */
    private static void emit(BufferBuilder buffer,
                             double cx, double cy, double cz,
                             double ux, double uy, double uz,
                             double vx, double vy, double vz,
                             int segment, int row) {
        float u0 = segment * 0.5f;
        float u1 = u0 + 0.5f;
        float v0 = row * 0.5f;
        float v1 = v0 + 0.5f;

        buffer.addVertex((float) (cx - ux - vx), (float) (cy - uy - vy), (float) (cz - uz - vz))
                .setUv(u0, v1);
        buffer.addVertex((float) (cx + ux - vx), (float) (cy + uy - vy), (float) (cz + uz - vz))
                .setUv(u1, v1);
        buffer.addVertex((float) (cx + ux + vx), (float) (cy + uy + vy), (float) (cz + uz + vz))
                .setUv(u1, v0);
        buffer.addVertex((float) (cx - ux + vx), (float) (cy - uy + vy), (float) (cz - uz + vz))
                .setUv(u0, v0);
    }
}
