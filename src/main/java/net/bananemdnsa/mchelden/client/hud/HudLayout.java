package net.bananemdnsa.mchelden.client.hud;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Wo die MCHelden-Anzeigen sitzen. Alle Elemente holen ihre Position hier, damit sich der
 * Stapel über eine einzige Zahl verschieben lässt.
 *
 * <p>Der Combat-Timer hängt bewusst nicht im unteren Stapel, sondern oben: im Kampf schaut
 * niemand an den unteren Bildschirmrand, und dort wurde er dadurch übersehen.
 *
 * <p>Die drei oberen Anzeigen teilen sich die Kanten, damit keine der anderen im Weg steht:
 * Bounty links, Timer mittig, Kontingente rechts.
 */
public final class HudLayout {
    /** Abstand der untersten Zeile über dem Bildschirmrand, oberhalb der Vanilla-Leisten. */
    private static final int BASE_OFFSET = 51;
    /** Abstand der oberen Anzeigen vom oberen Bildschirmrand. */
    private static final int TOP_OFFSET = 12;
    /** Abstand von linker und rechter Bildschirmkante. */
    private static final int EDGE_OFFSET = 8;

    private HudLayout() {
    }

    /** Unterste Zeile: die Herzen. */
    public static int heartsTop(GuiGraphics graphics) {
        return graphics.guiHeight() - BASE_OFFSET;
    }

    /** Oben links: das Bounty-Ziel. */
    public static int bountyTop(GuiGraphics graphics) {
        return TOP_OFFSET;
    }

    public static int bountyLeft(GuiGraphics graphics) {
        return EDGE_OFFSET;
    }

    /** Oben rechts: die Kontingente. */
    public static int quotaTop(GuiGraphics graphics) {
        return TOP_OFFSET;
    }

    /** Rechte Kante für einen rechtsbündigen Block. */
    public static int quotaRight(GuiGraphics graphics) {
        return graphics.guiWidth() - EDGE_OFFSET;
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
