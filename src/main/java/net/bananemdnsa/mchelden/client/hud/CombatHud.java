package net.bananemdnsa.mchelden.client.hud;

import net.bananemdnsa.mchelden.MCHelden;
import net.bananemdnsa.mchelden.client.ClientState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Der Combat-Timer in Rot. Gezeichnet wird er von {@link SegmentBar}. */
public final class CombatHud {
    public static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "combat");

    private static final SegmentBar.Palette PALETTE = new SegmentBar.Palette(
            0xFF000000, 0xFF1E0A0C, 0xFFE8564F, 0xFFFF8F89, 0xFFFFF0EC, 0xFFFFB4B4);

    private CombatHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        SegmentBar.render(graphics, delta, ClientState.combat(), PALETTE, "mchelden.combat.label");
    }
}
