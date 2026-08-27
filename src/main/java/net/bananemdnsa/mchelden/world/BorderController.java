package net.bananemdnsa.mchelden.world;

import net.bananemdnsa.mchelden.state.GameState;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.border.WorldBorder;

/**
 * Die Weltborder im Final War.
 *
 * <p><b>Die Mod speichert dafuer nichts und tickt nichts.</b> Ein einziger Aufruf von
 * {@link WorldBorder#lerpSizeBetween}, danach rechnet Vanilla: Interpolation, Pakete an die
 * Clients, und die rote Farbe, weil {@code getStatus()} bei kleinerem Ziel
 * {@code SHRINKING} meldet.
 *
 * <p><b>Der Neustart ist damit gratis.</b> {@code WorldBorder.Settings} legt einen
 * laufenden Lauf als <em>von der aktuellen Groesse, zum Ziel, in der Restzeit</em> ab —
 * {@code sizeLerpTime = border.getLerpRemainingTime()} — und {@code applySettings} stellt
 * beim Laden genau das wieder her. Ein Absturz mitten im Final War kostet keine Sekunde
 * Arena.
 *
 * <p>Alles Angezeigte wird deswegen jeden Tick frisch hier abgefragt statt mitgefuehrt. Es
 * gibt keinen zweiten Wert, der vom ersten abweichen koennte — dieselbe Ueberlegung, die
 * bei der {@link SafeZone} den gespeicherten Mittelpunkt weggeraeumt hat.
 */
public final class BorderController {
    /** Kantenlaenge der Welt. Die Border steht bei plusminus 1000. */
    public static final double START_SIZE = 2000.0;

    /** Kantenlaenge der Arena am Ende: zehn mal zehn Chunks. */
    public static final double FINAL_SIZE = 160.0;

    /** Dauer, wenn niemand eine nennt. Zweieinhalb Stunden. */
    public static final long DEFAULT_DURATION_MILLIS = 9_000_000L;

    private BorderController() {
    }

    /**
     * Wie voll der Balken der Bossbar steht: 1 bei 2000, 0 bei 160.
     *
     * <p>Reine Rechnung, damit sie ohne Spielstart pruefbar ist. Geklammert, weil ein Op
     * die Border jederzeit von Hand woanders hinsetzen darf.
     */
    public static float progress(double size) {
        return (float) Mth.clamp((size - FINAL_SIZE) / (START_SIZE - FINAL_SIZE), 0.0, 1.0);
    }

    /**
     * Setzt die Border auf Weltgroesse — aber nur beim allerersten Start einer Welt.
     *
     * <p>Ohne das haette eine frische Welt die Vanilla-Border von sechzig Millionen, und
     * die 2000 aus der Spec waeren Handarbeit, die genau einmal jemand vergisst.
     *
     * <p><b>Nur einmal, nicht bei jedem Start.</b> Sonst wuerfe ein Serverneustart mitten
     * im Final War die Arena zurueck auf 2000 — und die Wiederaufnahme, die Vanilla gratis
     * mitbringt, waere umsonst. Aus demselben Grund bleibt ein von Hand gesetztes Ziel
     * stehen.
     */
    public static void initialise(MinecraftServer server) {
        GameState state = GameState.get(server);
        if (state.isBorderSet()) {
            return;
        }

        reset(server);
        state.setBorderSet(true);
    }

    /** Startet den Lauf des Final War: von hier auf 160, ueber die angegebene Dauer. */
    public static void startFinalWar(MinecraftServer server, long millis) {
        shrink(server, FINAL_SIZE, millis);
    }

    /** Das nackte Werkzeug hinter {@code /helden border shrink}. */
    public static void shrink(MinecraftServer server, double target, long millis) {
        WorldBorder border = border(server);
        border.lerpSizeBetween(border.getSize(), target, millis);
    }

    /** Zurueck auf Weltgroesse, sofort. Bricht einen laufenden Lauf mit ab. */
    public static void reset(MinecraftServer server) {
        border(server).setSize(START_SIZE);
    }

    public static double size(MinecraftServer server) {
        return border(server).getSize();
    }

    /** Restzeit des laufenden Laufs in Millisekunden, oder 0, wenn die Border steht. */
    public static long remainingMillis(MinecraftServer server) {
        return Math.max(0L, border(server).getLerpRemainingTime());
    }

    /**
     * Abstand, den eine hereingeholte Position zur Kante behaelt.
     *
     * <p>Direkt auf der Kante stuende man im Warnbereich und naehme beim naechsten
     * Schrumpfschritt sofort Schaden — gerettet und trotzdem am Sterben.
     */
    public static final double RESCUE_MARGIN = 8.0;

    /** Liegt dieser Punkt ausserhalb der Border? */
    public static boolean isOutside(WorldBorder border, double x, double z) {
        return x < border.getMinX() || x > border.getMaxX()
                || z < border.getMinZ() || z > border.getMaxZ();
    }

    /**
     * Klemmt eine Position waagerecht in die Border, mit Abstand zur Kante.
     *
     * <p>Reine Rechnung, damit sie ohne Spielstart pruefbar ist. Die Hoehe bleibt aussen
     * vor: der Boden wird danach gesucht, und eine Border kennt ohnehin kein Oben.
     *
     * <p>Der Abstand wird gedeckelt, damit eine winzige Border die Klammer nicht umdreht —
     * sonst laege die untere Grenze ueber der oberen und das Ergebnis ausserhalb.
     *
     * @return die erlaubte Position als {@code {x, z}}
     */
    public static double[] clampInside(WorldBorder border, double x, double z) {
        double margin = Math.max(0.0, Math.min(RESCUE_MARGIN, border.getSize() / 2.0 - 1.0));

        return new double[] {
                Mth.clamp(x, border.getMinX() + margin, border.getMaxX() - margin),
                Mth.clamp(z, border.getMinZ() + margin, border.getMaxZ() - margin)};
    }

    private static WorldBorder border(MinecraftServer server) {
        return server.overworld().getWorldBorder();
    }
}
