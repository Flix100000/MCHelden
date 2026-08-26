package net.bananemdnsa.mchelden.client.hud;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Wo die MCHelden-Anzeigen sitzen. Alle Elemente holen ihre Position hier, damit sich der
 * gesamte Stapel über eine einzige Zahl verschieben lässt.
 *
 * <p>Die Zeilen haben feste Höhen, auch wenn eine davon gerade leer ist. Ein Stapel, der
 * springt sobald der Kampf beginnt, wäre im Kampf unlesbar.
 *
 * <p>Jede Zeile bekommt die Höhe, die ihr Inhalt braucht — der Combat-Timer trägt Rahmen
 * und Beschriftung und ist damit doppelt so hoch wie eine Herzenreihe. Einheitliche
 * Zeilenhöhen würden die Beschriftung in die Herzen hineinragen lassen.
 */
public final class HudLayout {
    /** Abstand der untersten Zeile über dem Bildschirmrand, oberhalb der Vanilla-Leisten. */
    private static final int BASE_OFFSET = 51;

    private static final int HEART_ROW = 11;
    /** Rahmen, Felder und die Beschriftung darunter. */
    private static final int COMBAT_ROW = 26;
    private static final int BOUNTY_ROW = 12;

    private HudLayout() {
    }

    /** Unterste Zeile: die Herzen. */
    public static int heartsTop(GuiGraphics graphics) {
        return graphics.guiHeight() - BASE_OFFSET;
    }

    /** Darüber: der Combat-Timer. */
    public static int combatTop(GuiGraphics graphics) {
        return heartsTop(graphics) - COMBAT_ROW;
    }

    /** Ganz oben: das Bounty-Ziel. */
    public static int bountyTop(GuiGraphics graphics) {
        return combatTop(graphics) - BOUNTY_ROW;
    }

    /** Höhe der Herzenreihe, für Elemente die daran anschliessen. */
    public static int heartRowHeight() {
        return HEART_ROW;
    }

    /** Linke Kante für einen mittig ausgerichteten Block. */
    public static int centered(GuiGraphics graphics, int width) {
        return (graphics.guiWidth() - width) / 2;
    }
}
