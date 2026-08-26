package net.bananemdnsa.mchelden.client.screen;

import net.bananemdnsa.mchelden.MCHelden;
import net.bananemdnsa.mchelden.grave.GraveMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import com.mojang.authlib.GameProfile;

/**
 * Der Grab-Bildschirm: Kopfzeile mit Kopf, Name, Todeszeitpunkt und gespeicherter XP,
 * darunter die Plätze.
 *
 * <p>Die Kopfzeile ist flach gehalten, weil dieser Bildschirm im Projekt dutzende Male pro
 * Spieler aufgeht. Was man oft sieht, darf nicht feierlich sein.
 */
public class GraveScreen extends AbstractContainerScreen<GraveMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "textures/gui/grave.png");

    private static final int HEADER_TOP = 18;
    private static final int HEAD_SIZE = 16;
    private static final int PADDING = 11;

    private static final int NAME_COLOR = 0xFF2B2B2B;
    private static final int SUBTLE_COLOR = 0xFF5A5A5A;
    private static final int XP_COLOR = 0xFF2B6A1F;

    private ItemStack head = ItemStack.EMPTY;

    public GraveScreen(GraveMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 190;
        this.inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();

        // Die Profil-ID darf nicht fehlen — Minecraft wirft sonst beim Oeffnen.
        head = new ItemStack(Items.PLAYER_HEAD);
        if (menu.getOwnerId() != null && !menu.getOwnerName().isEmpty()) {
            head.set(DataComponents.PROFILE,
                    new ResolvableProfile(new GameProfile(menu.getOwnerId(), menu.getOwnerName())));
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);

        int headX = PADDING;
        int headY = HEADER_TOP + 2;
        graphics.renderItem(head, headX, headY);

        int textX = headX + HEAD_SIZE + 5;
        graphics.drawString(font, Component.translatable("mchelden.grave.owner", menu.getOwnerName()),
                textX, headY + 1, NAME_COLOR, false);
        graphics.drawString(font, elapsedSince(), textX, headY + 11, SUBTLE_COLOR, false);

        Component xp = Component.translatable("mchelden.grave.xp", menu.getStoredXp());
        graphics.drawString(font, xp, imageWidth - PADDING - font.width(xp), headY + 6, XP_COLOR, false);
    }

    /** Wie lange der Tod her ist. Die Weltzeit kennt der Client, der Todeszeitpunkt kam mit. */
    private Component elapsedSince() {
        Minecraft minecraft = Minecraft.getInstance();
        long ticks = minecraft.level == null ? 0 : minecraft.level.getGameTime() - menu.getDiedAt();
        int minutes = (int) Math.max(0, ticks / (20 * 60));

        return minutes < 1
                ? Component.translatable("mchelden.grave.just_now")
                : Component.translatable("mchelden.grave.minutes_ago", minutes);
    }
}
