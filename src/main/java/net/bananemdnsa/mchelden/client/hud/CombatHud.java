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
 * Der Combat-Timer: sechs Felder in einem Rahmen, eines je möglichem Treffer.
 *
 * <p>Die Segmentierung ist keine Verzierung. Der Timer besteht mechanisch aus
 * 30-Sekunden-Blöcken — ein durchgehender Balken würde verschlucken, wie oft jemand
 * getroffen wurde. Sechs Felder machen die Regel nebenbei selbsterklärend.
 */
public final class CombatHud {
    public static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "combat");

    private static final int SEGMENTS = CombatTracker.MAX_TICKS / CombatTracker.HIT_TICKS;
    private static final int SEGMENT_WIDTH = 18;
    private static final int SEGMENT_HEIGHT = 7;
    private static final int SEGMENT_GAP = 2;
    /** Abstand zwischen Rahmenkante und Feldern. */
    private static final int PADDING = 2;
    private static final int BORDER = 1;

    private static final int FRAME = 0xFF000000;
    private static final int TRACK = 0xFF1E0A0C;
    private static final int FILL = 0xFFE8564F;
    private static final int FILL_TOP = 0xFFFF8F89;
    private static final int FLASH = 0xFFFFF0EC;
    private static final int TEXT = 0xFFFFB4B4;

    /** Ab wann der Balken pulsiert, weil der Kampf gleich vorbei ist. */
    private static final int WARNING_TICKS = 3 * 20;

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

        // Beim Einschweben von unten heraufziehen, beim Ausblenden nach oben davonziehen.
        float alpha = exit > 0f ? exit : enter;
        int lift = Math.round(exit > 0f ? -(1f - exit) * 3f : (1f - enter) * 4f);
        if (alpha <= 0.01f) {
            return;
        }

        int innerWidth = SEGMENTS * SEGMENT_WIDTH + (SEGMENTS - 1) * SEGMENT_GAP;
        int outerWidth = innerWidth + 2 * (PADDING + BORDER);
        int outerHeight = SEGMENT_HEIGHT + 2 * (PADDING + BORDER);

        int left = HudLayout.centered(graphics, outerWidth);
        int top = HudLayout.combatTop(graphics) + lift;

        RenderSystem.enableBlend();
        drawFrame(graphics, left, top, outerWidth, outerHeight, alpha);
        drawSegments(graphics, left + PADDING + BORDER, top + PADDING + BORDER, partial, exit, alpha);
        drawLabel(graphics, minecraft.font, top + outerHeight + 2, alpha);
        RenderSystem.disableBlend();
    }

    private static void drawFrame(GuiGraphics graphics, int left, int top, int width, int height, float alpha) {
        graphics.fill(left, top, left + width, top + height, withAlpha(FRAME, alpha));
        graphics.fill(left + BORDER, top + BORDER, left + width - BORDER, top + height - BORDER,
                withAlpha(TRACK, alpha));
    }

    private static void drawSegments(GuiGraphics graphics, int left, int top,
                                     float partial, float exit, float alpha) {
        int ticks = ClientState.getCombatTicks();
        int full = ticks / CombatTracker.HIT_TICKS;
        float partialSegment = (ticks % CombatTracker.HIT_TICKS) / (float) CombatTracker.HIT_TICKS;

        float flash = ClientState.combatFlash(partial);
        // Kurz vor Schluss pulsiert der ganze Balken — gleich darf man wieder an die Kisten.
        float warning = ticks > 0 && ticks <= WARNING_TICKS
                ? (Mth.sin(ticks * 0.45f) * 0.5f + 0.5f) * 0.6f
                : 0f;
        // Beim Ausblenden leuchtet er einmal hell auf, statt einfach zu verschwinden.
        float highlight = Math.max(Math.max(flash, warning), exit > 0.55f ? (exit - 0.55f) / 0.45f : 0f);

        int base = blend(FILL, FLASH, highlight);
        int cap = blend(FILL_TOP, FLASH, highlight);

        for (int index = 0; index < SEGMENTS; index++) {
            int x = left + index * (SEGMENT_WIDTH + SEGMENT_GAP);
            float fillPortion = index < full ? 1f : index == full ? partialSegment : 0f;
            if (fillPortion <= 0f) {
                continue;
            }

            int filled = Math.max(1, Math.round(SEGMENT_WIDTH * fillPortion));
            graphics.fill(x, top, x + filled, top + SEGMENT_HEIGHT, withAlpha(base, alpha));
            // Hellere Kante oben gibt dem Balken Tiefe, so wie Vanilla es macht.
            graphics.fill(x, top, x + filled, top + 2, withAlpha(cap, alpha));
        }
    }

    private static void drawLabel(GuiGraphics graphics, Font font, int top, float alpha) {
        Component label = Component.translatable("mchelden.combat.label",
                formatTime(ClientState.getCombatTicks()));
        int width = font.width(label);

        graphics.drawString(font, label, (graphics.guiWidth() - width) / 2, top,
                withAlpha(TEXT, alpha), true);
    }

    private static String formatTime(int ticks) {
        int seconds = Mth.ceil(ticks / 20f);
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private static int withAlpha(int color, float alpha) {
        int value = (int) ((color >>> 24) * Mth.clamp(alpha, 0f, 1f));
        return (value << 24) | (color & 0x00FFFFFF);
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
