package net.bananemdnsa.mchelden.client.hud;

import net.bananemdnsa.mchelden.MCHelden;
import net.bananemdnsa.mchelden.client.ClientState;
import net.bananemdnsa.mchelden.combat.CombatTracker;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Der Combat-Timer als Balken mit Restzeit.
 *
 * <p>Der Balken misst gegen die vollen drei Minuten, nicht gegen den zuletzt gesetzten Wert.
 * Damit sagt seine Länge nicht nur aus, wie lange es noch dauert, sondern auch wie tief man
 * im Kampf steckt: ein Streifschuss ergibt einen kurzen Balken, ein Schlagabtausch einen vollen.
 */
public final class CombatHud {
    public static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "combat");

    private static final int BAR_WIDTH = 80;
    private static final int BAR_HEIGHT = 3;
    /** Abstand zwischen Balken und Restzeit. */
    private static final int GAP = 5;

    private static final int BAR_BACKGROUND = 0xA0140406;
    private static final int BAR_FILL = 0xFFE05555;
    private static final int BAR_FLASH = 0xFFFFE0E0;
    private static final int TEXT_COLOR = 0xFFFFB4B4;

    private CombatHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || !ClientState.isInCombat()) {
            return;
        }

        float partial = delta.getGameTimeDeltaPartialTick(false);
        Font font = minecraft.font;

        String time = formatTime(ClientState.getCombatTicks());
        int textWidth = font.width(time);
        int left = HudLayout.centered(graphics, BAR_WIDTH + GAP + textWidth);
        int top = HudLayout.combatTop(graphics);

        int barTop = top + (font.lineHeight - BAR_HEIGHT) / 2;
        graphics.fill(left, barTop, left + BAR_WIDTH, barTop + BAR_HEIGHT, BAR_BACKGROUND);

        float portion = Mth.clamp(ClientState.getCombatTicks() / (float) CombatTracker.MAX_TICKS, 0f, 1f);
        int filled = Math.max(1, Math.round(BAR_WIDTH * portion));

        // Nur hochspringen reicht nicht — im Kampf schaut niemand aufs HUD. Das kurze
        // Aufleuchten faengt den Blick auch aus dem Augenwinkel.
        int fill = blend(BAR_FILL, BAR_FLASH, ClientState.combatFlash(partial));
        graphics.fill(left, barTop, left + filled, barTop + BAR_HEIGHT, fill);

        graphics.drawString(font, time, left + BAR_WIDTH + GAP, top, TEXT_COLOR, true);
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
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
