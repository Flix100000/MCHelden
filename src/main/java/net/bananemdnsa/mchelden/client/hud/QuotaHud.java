package net.bananemdnsa.mchelden.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;

import net.bananemdnsa.mchelden.MCHelden;
import net.bananemdnsa.mchelden.client.ClientState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Die Kontingente oben rechts, plus die rote Plane über aufgebrauchten Hotbar-Items.
 *
 * <p>Ein Zähler erscheint nur, wenn der Spieler das Item tatsächlich dabeihat. Ein
 * Spinnweben-Zähler bei jemandem ohne Spinnweben wäre reines Rauschen.
 */
public final class QuotaHud {
    public static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "quota");

    private static final int ICON = 16;
    private static final int ROW_HEIGHT = 18;
    private static final int GAP = 4;

    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_EMPTY = 0xFFE05555;
    /** Rote Plane über einem Item, dessen Kontingent leer ist. */
    private static final int SPENT_OVERLAY = 0x99C01818;

    /** Vanilla zeichnet die Hotbar 182 breit und die Items drei Pixel innerhalb. */
    private static final int HOTBAR_HALF_WIDTH = 91;
    private static final int HOTBAR_ITEM_INSET = 3;
    private static final int HOTBAR_SLOT_PITCH = 20;
    private static final int HOTBAR_ITEM_BOTTOM = 19;

    private QuotaHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || !ClientState.isInCombat()) {
            return;
        }

        RenderSystem.enableBlend();
        int row = renderCounter(graphics, minecraft.font, player, 0,
                Items.ENDER_PEARL.getDefaultInstance(), ClientState.getPearlsLeft());
        renderCounter(graphics, minecraft.font, player, row,
                Items.COBWEB.getDefaultInstance(), ClientState.getCobwebsLeft());

        markSpentHotbarSlots(graphics, player);
        RenderSystem.disableBlend();
    }

    /**
     * Zeichnet eine Zeile, sofern der Spieler das Item dabeihat.
     *
     * @return die nächste freie Zeile
     */
    private static int renderCounter(GuiGraphics graphics, Font font, LocalPlayer player,
                                     int row, ItemStack icon, int left) {
        if (!carries(player, icon)) {
            return row;
        }

        String text = String.valueOf(left);
        int textWidth = font.width(text);
        int right = HudLayout.quotaRight(graphics);
        int top = HudLayout.quotaTop(graphics) + row * ROW_HEIGHT;

        graphics.drawString(font, text, right - textWidth, top + (ICON - font.lineHeight) / 2,
                left == 0 ? TEXT_EMPTY : TEXT, true);
        graphics.renderItem(icon, right - textWidth - GAP - ICON, top);

        return row + 1;
    }

    /**
     * Legt eine rote Plane über Hotbar-Items, deren Kontingent aufgebraucht ist.
     *
     * <p>So sieht man beim Durchschalten sofort, dass das Item nichts mehr bringt, statt es
     * erst zu probieren und im Kampf eine Sekunde zu verlieren.
     */
    private static void markSpentHotbarSlots(GuiGraphics graphics, LocalPlayer player) {
        boolean pearlsSpent = ClientState.getPearlsLeft() <= 0;
        boolean cobwebsSpent = ClientState.getCobwebsLeft() <= 0;
        if (!pearlsSpent && !cobwebsSpent) {
            return;
        }

        int left = graphics.guiWidth() / 2 - HOTBAR_HALF_WIDTH + HOTBAR_ITEM_INSET;
        int top = graphics.guiHeight() - HOTBAR_ITEM_BOTTOM;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            boolean spent = (pearlsSpent && stack.is(Items.ENDER_PEARL))
                    || (cobwebsSpent && stack.is(Items.COBWEB));
            if (!spent) {
                continue;
            }

            int x = left + slot * HOTBAR_SLOT_PITCH;
            graphics.fill(x, top, x + ICON, top + ICON, SPENT_OVERLAY);
        }
    }

    private static boolean carries(LocalPlayer player, ItemStack icon) {
        return player.getInventory().contains(icon);
    }
}
