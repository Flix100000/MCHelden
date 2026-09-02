package net.bananemdnsa.mchelden.client.hud;

import net.bananemdnsa.mchelden.MCHelden;
import net.bananemdnsa.mchelden.client.ClientState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Der Duell-Timer in Blau, am selben Platz wie der Combat-Timer.
 *
 * <p>Dieselbe Form, andere Farbe: das Duell ist ein Kampf, nur einer ohne Einsatz. Blau
 * setzt sich vom roten Combat-Timer ab und knuepft an das blaue vierte Herz an.
 */
public final class DuelHud {
    public static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "duel");

    private static final SegmentBar.Palette PALETTE = new SegmentBar.Palette(
            0xFF000000, 0xFF0A141E, 0xFF4FA8E8, 0xFF89CFFF, 0xFFECF7FF, 0xFFB4DCFF);

    private DuelHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        SegmentBar.render(graphics, delta, ClientState.duel(), PALETTE, "mchelden.duel.label");
    }
}
