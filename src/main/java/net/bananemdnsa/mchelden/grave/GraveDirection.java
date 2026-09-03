package net.bananemdnsa.mchelden.grave;

import net.minecraft.core.BlockPos;

/**
 * Wie weit und in welche Richtung das Grab liegt.
 *
 * <p>Koordinaten allein helfen wenig, wenn man gerade am Bett aufwacht. „187 Blöcke
 * nordöstlich" sagt einem sofort, ob man rennen kann oder ein Boot braucht.
 *
 * <p>Reine Rechnung ohne Welt und ohne Server — der einzige Teil der Respawn-Nachricht,
 * der sich ohne laufendes Spiel prüfen lässt.
 */
public final class GraveDirection {

    /** Die acht Sektoren im Uhrzeigersinn ab Norden. */
    private static final String[] KEYS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    private static final double SECTOR = 360.0 / KEYS.length;

    private GraveDirection() {
    }

    /**
     * Waagerechter Abstand in Blöcken, gerundet.
     *
     * <p>Die Höhe zählt nicht mit: „187 Blöcke nordöstlich" beschreibt einen Weg, den man
     * geht, und ein Grab dreissig Blöcke tiefer liegt trotzdem nordöstlich.
     */
    public static int distance(BlockPos from, BlockPos to) {
        double dx = (double) to.getX() - from.getX();
        double dz = (double) to.getZ() - from.getZ();
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }

    /** Ob beide Punkte in derselben Säule liegen — dann gibt es keine Richtung zu nennen. */
    public static boolean sameSpot(BlockPos from, BlockPos to) {
        return from.getX() == to.getX() && from.getZ() == to.getZ();
    }

    /**
     * Der Sprachschlüssel der Himmelsrichtung, etwa {@code mchelden.direction.ne}.
     *
     * <p>In Minecraft zeigt Norden nach -Z und Osten nach +X. Der Winkel wird deswegen aus
     * {@code (dx, -dz)} gebildet: dann liegt Norden bei 0° und Osten bei 90°, wie auf einem
     * Kompass.
     *
     * <p>Die halbe Sektorbreite im Versatz sorgt dafür, dass jede Richtung ihren Namen
     * mittig trägt — Norden reicht von 337,5° bis 22,5°, nicht von 0° bis 45°.
     */
    public static String key(BlockPos from, BlockPos to) {
        double dx = (double) to.getX() - from.getX();
        double dz = (double) to.getZ() - from.getZ();

        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        double turned = (angle + SECTOR / 2 + 360.0) % 360.0;

        return "mchelden.direction." + KEYS[(int) (turned / SECTOR) % KEYS.length];
    }
}
