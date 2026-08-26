package net.bananemdnsa.mchelden.client.hud;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Wo die MCHelden-Anzeigen sitzen. Alle Elemente holen ihre Position hier, damit sich der
 * Stapel über eine einzige Zahl verschieben lässt.
 *
 * <p>Der Combat-Timer hängt bewusst nicht im unteren Stapel, sondern oben: im Kampf schaut
 * niemand an den unteren Bildschirmrand, und dort wurde er dadurch übersehen.
 */
public final class HudLayout {
    /** Abstand der untersten Zeile über dem Bildschirmrand, oberhalb der Vanilla-Leisten. */
    private static final int BASE_OFFSET = 51;
    private static final int BOUNTY_ROW = 12;

    /** Abstand des Combat-Timers vom oberen Bildschirmrand. */
    private static final int TOP_OFFSET = 12;

    private HudLayout() {
    }

    /** Unterste Zeile: die Herzen. */
    public static int heartsTop(GuiGraphics graphics) {
        return graphics.guiHeight() - BASE_OFFSET;
    }

    /** Direkt darüber: das Bounty-Ziel. */
    public static int bountyTop(GuiGraphics graphics) {
        return heartsTop(graphics) - BOUNTY_ROW;
    }

    /** Oben mittig: der Combat-Timer. */
    public static int combatTop(GuiGraphics graphics) {
        return TOP_OFFSET;
    }

    /** Linke Kante für einen mittig ausgerichteten Block. */
    public static int centered(GuiGraphics graphics, int width) {
        return (graphics.guiWidth() - width) / 2;
    }
}
