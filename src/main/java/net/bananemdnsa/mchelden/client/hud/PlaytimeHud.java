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
        int seconds = ClientState.getPlaytimeRemainingSeconds();
        String text = format(seconds);
        int width = font.width(text);

        int right = HudLayout.quotaRight(graphics);
        int top = HudLayout.quotaTop(graphics);

        RenderSystem.enableBlend();
        graphics.drawString(font, text, right - width, top + (ICON - font.lineHeight) / 2,
                colorFor(seconds), true);
        graphics.renderItem(new ItemStack(Items.CLOCK), right - width - GAP - ICON, top);
        RenderSystem.disableBlend();
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
