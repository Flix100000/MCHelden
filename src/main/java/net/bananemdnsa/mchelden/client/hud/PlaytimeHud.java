package net.bananemdnsa.mchelden.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;

import net.bananemdnsa.mchelden.MCHelden;
import net.bananemdnsa.mchelden.client.ClientState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Die verbleibende Spielzeit der Aufbauphase, oben rechts.
 *
 * <p>Dauerhaft sichtbar, solange das Limit gilt. Der Server schickt {@code -1} fuer "kein
 * Limit" — das deckt Ops und alle Phasen ausser Aufbau mit derselben Zahl ab, ohne ein
 * zusaetzliches Feld.
 *
 * <p>Ein laufendes Zeit-Event ist ein dritter Zustand: die Zahl bleibt stehen, grau und
 * reglos, statt zu verschwinden oder weiterzulaufen. Eine verschwundene Uhr saehe nach
 * einem Fehler aus, eine weiterlaufende waere eine Luege — die Uhr steht ja tatsaechlich.
 *
 * <p>Die Farbe traegt die Dringlichkeit: die Warnungen selbst kommen als Chat-Zeilen, und
 * eine Zeile im Chat ist weg, sobald darunter etwas anderes steht. Die Uhr bleibt.
 */
public final class PlaytimeHud {
    public static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "playtime");

    private static final int ICON = 16;
    private static final int GAP = 4;

    private static final int NORMAL = 0xFFFFFFFF;
    private static final int LOW = 0xFFE0A030;
    private static final int CRITICAL = 0xFFE05555;
    /** Die Dringlichkeits-Farben bedeuten nichts, waehrend die Uhr steht. */
    private static final int PAUSED = 0xFFA0A0A0;

    /** Ab hier wird die Uhr bernstein, ab der zweiten Schwelle rot. */
    private static final int LOW_SECONDS = 600;
    private static final int CRITICAL_SECONDS = 60;

    private PlaytimeHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || !ClientState.hasPlaytimeLimit()) {
            return;
        }

        Font font = minecraft.font;
        boolean paused = ClientState.isPlaytimePaused();
        int seconds = ClientState.getPlaytimeRemainingSeconds();
        String text = format(seconds);
        int width = font.width(text);

        int right = HudLayout.quotaRight(graphics);
        int top = HudLayout.quotaTop(graphics);
        int iconX = right - width - GAP - ICON;

        RenderSystem.enableBlend();
        graphics.drawString(font, text, right - width, top + (ICON - font.lineHeight) / 2,
                paused ? PAUSED : colorFor(seconds), true);
        if (paused) {
            drawPauseIcon(graphics, iconX, top);
        } else {
            graphics.renderItem(new ItemStack(Items.CLOCK), iconX, top);
        }
        RenderSystem.disableBlend();
    }

    /**
     * Zwei senkrechte Balken statt des Uhr-Items — Minecrafts Font hat kein verlaessliches
     * Pause-Symbol, deswegen wird gemalt statt geschrieben. Im selben 16x16-Kasten wie sonst
     * das Item, damit sich am Layout zwischen laufendem und pausiertem Zustand nichts
     * verschiebt.
     */
    private static void drawPauseIcon(GuiGraphics graphics, int iconX, int top) {
        graphics.fill(iconX + 4, top + 3, iconX + 7, top + 13, PAUSED);
        graphics.fill(iconX + 10, top + 3, iconX + 13, top + 13, PAUSED);
    }

    private static int colorFor(int seconds) {
        if (seconds <= CRITICAL_SECONDS) {
            return CRITICAL;
        }
        return seconds <= LOW_SECONDS ? LOW : NORMAL;
    }

    private static String format(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
