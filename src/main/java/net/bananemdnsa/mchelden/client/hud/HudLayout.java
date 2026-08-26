package net.bananemdnsa.mchelden.client.hud;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Wo die MCHelden-Anzeigen sitzen. Alle Elemente holen ihre Position hier, damit sich der
 * gesamte Stapel über eine einzige Zahl verschieben lässt.
 *
 * <p>Die Zeilen haben feste Höhen, auch wenn eine davon gerade leer ist. Ein Stapel, der
 * springt sobald der Kampf beginnt, wäre im Kampf unlesbar.
 */
public final class HudLayout {
    /** Abstand der untersten Zeile über dem Bildschirmrand, oberhalb der Vanilla-Leisten. */
    private static final int BASE_OFFSET = 51;
    private static final int ROW_HEIGHT = 11;

    private HudLayout() {
    }

    /** Unterste Zeile: die Herzen. */
    public static int heartsTop(GuiGraphics graphics) {
        return graphics.guiHeight() - BASE_OFFSET;
    }

    /** Darüber: der Combat-Timer. */
    public static int combatTop(GuiGraphics graphics) {
        return heartsTop(graphics) - ROW_HEIGHT;
    }

    /** Ganz oben: das Bounty-Ziel. */
    public static int bountyTop(GuiGraphics graphics) {
        return combatTop(graphics) - ROW_HEIGHT;
    }

    /** Linke Kante für einen mittig ausgerichteten Block. */
    public static int centered(GuiGraphics graphics, int width) {
        return (graphics.guiWidth() - width) / 2;
    }
}
