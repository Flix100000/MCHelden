package net.bananemdnsa.mchelden.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;

import net.bananemdnsa.mchelden.MCHelden;
import net.bananemdnsa.mchelden.client.ClientState;
import net.bananemdnsa.mchelden.state.PlayerState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Die Leben-Reihe über der XP-Leiste.
 *
 * <p>Drei Zustände pro Slot, die auf einen Blick unterscheidbar sein müssen:
 * blaues Herz (vorhanden), dunkler Sockel (verloren), gestrichelter Umriss (das
 * Bounty-Herz, das man noch nie hatte).
 */
public final class HeartHud {
    public static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "hearts");

    private static final ResourceLocation CONTAINER =
            ResourceLocation.withDefaultNamespace("hud/heart/container");
    private static final ResourceLocation BLUE =
            ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "hud/heart_blue");
    private static final ResourceLocation BOUNTY_SLOT =
            ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "hud/heart_slot_empty");

    private static final int SPRITE = 9;
    private static final int SPACING = 11;
    /** Abstand über dem unteren Bildschirmrand, oberhalb der Vanilla-Statusleisten. */
    private static final int BOTTOM_OFFSET = 51;
    private static final float BOUNTY_SLOT_ALPHA = 0.35f;

    private HeartHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.player.isSpectator()) {
            return;
        }

        int hearts = ClientState.getHearts();
        int slots = PlayerState.MAX_HEARTS;

        int rowWidth = (slots - 1) * SPACING + SPRITE;
        int left = (graphics.guiWidth() - rowWidth) / 2;
        int top = graphics.guiHeight() - BOTTOM_OFFSET;

        RenderSystem.enableBlend();
        for (int slot = 0; slot < slots; slot++) {
            renderSlot(graphics, left + slot * SPACING, top, slot, hearts);
        }
        RenderSystem.disableBlend();
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    private static void renderSlot(GuiGraphics graphics, int x, int y, int slot, int hearts) {
        if (slot < hearts) {
            graphics.blitSprite(CONTAINER, x, y, SPRITE, SPRITE);
            graphics.blitSprite(BLUE, x, y, SPRITE, SPRITE);
            return;
        }

        // Der Slot, der gerade verloren geht: Sockel steht schon, das Herz zerfällt darüber.
        if (slot == hearts && ClientState.isLossAnimationRunning()) {
            graphics.blitSprite(CONTAINER, x, y, SPRITE, SPRITE);
            renderShatter(graphics, x, y);
            return;
        }

        if (slot < PlayerState.DEFAULT_HEARTS) {
            graphics.blitSprite(CONTAINER, x, y, SPRITE, SPRITE);
            return;
        }

        graphics.setColor(1f, 1f, 1f, BOUNTY_SLOT_ALPHA);
        graphics.blitSprite(BOUNTY_SLOT, x, y, SPRITE, SPRITE);
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    /** Das Herz blasst aus und rutscht dabei ein Stück nach unten weg. */
    private static void renderShatter(GuiGraphics graphics, int x, int y) {
        float progress = ClientState.lossAnimationProgress();
        int drop = Math.round((1f - progress) * 3f);

        graphics.setColor(1f, 1f, 1f, progress);
        graphics.blitSprite(BLUE, x, y + drop, SPRITE, SPRITE);
        graphics.setColor(1f, 1f, 1f, 1f);
    }
}
