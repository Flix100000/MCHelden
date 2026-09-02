package net.bananemdnsa.mchelden.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;

import net.bananemdnsa.mchelden.client.TimerState;
import net.bananemdnsa.mchelden.combat.HitTimer;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Ein Timer-Balken: sechs Felder in einem Rahmen, eines je möglichem Treffer, oben mittig.
 *
 * <p>Die Segmentierung ist keine Verzierung. Der Timer besteht mechanisch aus
 * 30-Sekunden-Blöcken — ein durchgehender Balken würde verschlucken, wie oft jemand
 * getroffen wurde. Sechs Felder machen die Regel nebenbei selbsterklärend.
 *
 * <p>Zwei Balken teilen sich diesen Code: der Kampf in Rot, das Duell in Blau. Sie sitzen am
 * selben Platz und schliessen sich gegenseitig aus — im Duell laeuft kein Combat-Timer, und
 * der Moment, in dem beides zusammentraefe, ist genau der, in dem das Duell platzt.
 */
public final class SegmentBar {
    private static final int SEGMENTS = HitTimer.MAX_TICKS / HitTimer.HIT_TICKS;
    private static final int SEGMENT_WIDTH = 15;
    private static final int SEGMENT_HEIGHT = 6;
    private static final int SEGMENT_GAP = 2;
    private static final int PADDING = 2;
    private static final int BORDER = 1;

    /**
     * Die Farben eines Balkens.
     *
     * @param frame Rahmen aussen
     * @param track leere Rinne dahinter
     * @param fill gefuellter Teil
     * @param fillTop hellere Kante oben, die dem Balken Tiefe gibt
     * @param flash Farbe beim Aufleuchten nach einem Treffer
     * @param text Farbe der Beschriftung
     */
    public record Palette(int frame, int track, int fill, int fillTop, int flash, int text) {
    }

    private SegmentBar() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta, TimerState timer,
                              Palette palette, String labelKey) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || !timer.isVisible()) {
            return;
        }

        float partial = delta.getGameTimeDeltaPartialTick(false);
        float exit = timer.exit(partial);
        float enter = timer.enter(partial);
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

        fillCut(graphics, left, top, left + outerWidth, top + outerHeight, palette.frame());
        fillCut(graphics, left + BORDER, top + BORDER,
                left + outerWidth - BORDER, top + outerHeight - BORDER, palette.track());
        drawSegments(graphics, left + PADDING + BORDER, top + PADDING + BORDER,
                timer, palette, partial, exit);
        drawLabel(graphics, minecraft.font, top + outerHeight + 3, timer, palette, labelKey);

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

    private static void drawSegments(GuiGraphics graphics, int left, int top, TimerState timer,
                                     Palette palette, float partial, float exit) {
        int ticks = timer.ticks();
        int full = ticks / HitTimer.HIT_TICKS;
        float partialSegment = (ticks % HitTimer.HIT_TICKS) / (float) HitTimer.HIT_TICKS;

        float flash = timer.flash(partial);
        // Harter Wechsel statt sanftem Wogen: ein weiches Pulsieren uebersieht man,
        // ein Blinken zwischen zwei klaren Zustaenden nicht.
        float warning = ticks > 0 && ticks <= TimerState.WARNING_TICKS
                ? (ticks % 10 < 5 ? 1f : 0f)
                : 0f;
        float highlight = Math.max(Math.max(flash, warning), exit > 0.55f ? (exit - 0.55f) / 0.45f : 0f);

        int base = blend(palette.fill(), palette.flash(), highlight);
        int cap = blend(palette.fillTop(), palette.flash(), highlight);

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

    private static void drawLabel(GuiGraphics graphics, Font font, int top, TimerState timer,
                                  Palette palette, String labelKey) {
        Component label = Component.translatable(labelKey, formatTime(timer.ticks()));
        graphics.drawString(font, label, (graphics.guiWidth() - font.width(label)) / 2, top,
                palette.text(), true);
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
