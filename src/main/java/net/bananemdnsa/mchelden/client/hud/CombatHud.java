package net.bananemdnsa.mchelden.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;

import net.bananemdnsa.mchelden.MCHelden;
import net.bananemdnsa.mchelden.client.ClientState;
import net.bananemdnsa.mchelden.combat.CombatTracker;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Der Combat-Timer: sechs Felder in einem Rahmen, eines je möglichem Treffer, oben mittig.
 *
 * <p>Die Segmentierung ist keine Verzierung. Der Timer besteht mechanisch aus
 * 30-Sekunden-Blöcken — ein durchgehender Balken würde verschlucken, wie oft jemand
 * getroffen wurde. Sechs Felder machen die Regel nebenbei selbsterklärend.
 */
public final class CombatHud {
    public static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "combat");

    private static final int SEGMENTS = CombatTracker.MAX_TICKS / CombatTracker.HIT_TICKS;
    private static final int SEGMENT_WIDTH = 15;
    private static final int SEGMENT_HEIGHT = 6;
    private static final int SEGMENT_GAP = 2;
    private static final int PADDING = 2;
    private static final int BORDER = 1;

    private static final int FRAME = 0xFF000000;
    private static final int TRACK = 0xFF1E0A0C;
    private static final int FILL = 0xFFE8564F;
    private static final int FILL_TOP = 0xFFFF8F89;
    private static final int FLASH = 0xFFFFF0EC;
    private static final int TEXT = 0xFFFFB4B4;

    private CombatHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || !ClientState.isCombatVisible()) {
            return;
        }

        float partial = delta.getGameTimeDeltaPartialTick(false);
        float exit = ClientState.combatExit(partial);
        float enter = ClientState.combatEnter(partial);
        float open = exit > 0f ? exit : enter;
        if (open <= 0.01f) {
            return;
        }

        int innerWidth = SEGMENTS * SEGMENT_WIDTH + (SEGMENTS - 1) * SEGMENT_GAP;
        int outerWidth = innerWidth + 2 * (PADDING + BORDER);
        int outerHeight = SEGMENT_HEIGHT + 2 * (PADDING + BORDER);

        int left = HudLayout.centered(graphics, outerWidth);
        int top = HudLayout.combatTop(graphics);

        // Aufziehen aus der Mitte statt Ein- und Ausblenden. Minecraft blendet nichts weich —
        // eine Alpha-Blende wirkt im Interface wie ein Fremdkoerper aus einem anderen Spiel.
        int visible = Math.max(2, Math.round(outerWidth * open));
        int clipLeft = left + (outerWidth - visible) / 2;

        RenderSystem.enableBlend();
        graphics.enableScissor(clipLeft, top, clipLeft + visible, top + outerHeight + 12);

        fillCut(graphics, left, top, left + outerWidth, top + outerHeight, FRAME);
        fillCut(graphics, left + BORDER, top + BORDER,
                left + outerWidth - BORDER, top + outerHeight - BORDER, TRACK);
        drawSegments(graphics, left + PADDING + BORDER, top + PADDING + BORDER, partial, exit);
        drawLabel(graphics, minecraft.font, top + outerHeight + 3);

        graphics.disableScissor();
        RenderSystem.disableBlend();
    }

    /**
     * Rechteck mit weggelassenen Eckpixeln.
     *
     * <p>So rundet Pixelgrafik ab. Echte weiche Kanten gaebe es nur unscharf, und in einem
     * Interface aus lauter harten Kanten faellt Unschaerfe sofort als falsch auf.
     */
    private static void fillCut(GuiGraphics graphics, int left, int top, int right, int bottom, int color) {
        graphics.fill(left + 1, top, right - 1, bottom, color);
        graphics.fill(left, top + 1, left + 1, bottom - 1, color);
        graphics.fill(right - 1, top + 1, right, bottom - 1, color);
    }

    private static void drawSegments(GuiGraphics graphics, int left, int top, float partial, float exit) {
        int ticks = ClientState.getCombatTicks();
        int full = ticks / CombatTracker.HIT_TICKS;
        float partialSegment = (ticks % CombatTracker.HIT_TICKS) / (float) CombatTracker.HIT_TICKS;

        float flash = ClientState.combatFlash(partial);
        // Harter Wechsel statt sanftem Wogen: ein weiches Pulsieren uebersieht man,
        // ein Blinken zwischen zwei klaren Zustaenden nicht.
        float warning = ticks > 0 && ticks <= ClientState.COMBAT_WARNING_TICKS
                ? (ticks % 10 < 5 ? 1f : 0f)
                : 0f;
        float highlight = Math.max(Math.max(flash, warning), exit > 0.55f ? (exit - 0.55f) / 0.45f : 0f);

        int base = blend(FILL, FLASH, highlight);
        int cap = blend(FILL_TOP, FLASH, highlight);

        for (int index = 0; index < SEGMENTS; index++) {
            int x = left + index * (SEGMENT_WIDTH + SEGMENT_GAP);
            float portion = index < full ? 1f : index == full ? partialSegment : 0f;
            if (portion <= 0f) {
                continue;
            }

            int filled = Math.max(1, Math.round(SEGMENT_WIDTH * portion));
            graphics.fill(x, top, x + filled, top + SEGMENT_HEIGHT, base);
            // Hellere Kante oben gibt dem Balken Tiefe, so wie Vanilla es macht.
            graphics.fill(x, top, x + filled, top + 2, cap);
        }
    }

    private static void drawLabel(GuiGraphics graphics, Font font, int top) {
        Component label = Component.translatable("mchelden.combat.label",
                formatTime(ClientState.getCombatTicks()));
        graphics.drawString(font, label, (graphics.guiWidth() - font.width(label)) / 2, top, TEXT, true);
    }

    private static String formatTime(int ticks) {
        int seconds = Mth.ceil(ticks / 20f);
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private static int blend(int from, int to, float amount) {
        if (amount <= 0f) {
            return from;
        }

        int red = Mth.lerpInt(amount, from >> 16 & 0xFF, to >> 16 & 0xFF);
        int green = Mth.lerpInt(amount, from >> 8 & 0xFF, to >> 8 & 0xFF);
        int blue = Mth.lerpInt(amount, from & 0xFF, to & 0xFF);
        return (from & 0xFF000000) | red << 16 | green << 8 | blue;
    }
}
