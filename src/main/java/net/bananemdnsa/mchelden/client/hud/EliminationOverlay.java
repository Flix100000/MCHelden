package net.bananemdnsa.mchelden.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.bananemdnsa.mchelden.MCHelden;
import net.bananemdnsa.mchelden.client.ClientState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

/**
 * Die Ansage, wenn jemand ausscheidet.
 *
 * <p>Selbst gezeichnet statt als Vanilla-Titel, aus demselben Grund wie beim Bounty-Roll:
 * der Vanilla-Titel kann nicht anders als vierfach vergroessern, und "%s ist ausgeschieden"
 * haengt damit links und rechts aus dem Bild. Hier passt sich die Zeile der Breite an.
 *
 * <p>Wo der Platz reicht, sieht sie aus wie vorher — dieselbe Stelle, dieselbe Groesse,
 * derselbe Hintergrundkasten, dieselbe Blende. Sie schrumpft nur, wenn sie muss.
 */
public final class EliminationOverlay {
    public static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "elimination");

    /** Die Groesse des Vanilla-Titels, solange der Platz reicht. */
    private static final float TITLE_SCALE = 4f;
    private static final float SUBTITLE_SCALE = 2f;
    /** Luft zu den Bildschirmkanten, damit die Zeile nicht an der Kante klebt. */
    private static final int MARGIN = 24;

    /**
     * Die Zeilen sitzen dort, wo Vanilla sie setzt: zehn Einheiten ueber der Bildschirmmitte
     * und fuenf darunter, jeweils in ihrer eigenen Groesse gerechnet. Damit rueckt beim
     * Schrumpfen auch der Abstand mit.
     */
    private static final int TITLE_OFFSET = -10;
    private static final int SUBTITLE_OFFSET = 5;

    /** Rot und Grau, dieselben Farben, in denen die Ansage vorher stand. */
    private static final int TITLE_COLOR = 0xFF5555;
    private static final int SUBTITLE_COLOR = 0xAAAAAA;

    /** Unter diesem Wert lohnt das Zeichnen nicht mehr — so haelt es auch Vanilla. */
    private static final int ALPHA_FLOOR = 8;

    private EliminationOverlay() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || !ClientState.isEliminationRunning()) {
            return;
        }

        float ticksLeft = ClientState.eliminationTicksLeft(delta.getGameTimeDeltaPartialTick(false));
        int alpha = EliminationAnnouncement.alphaFor(ticksLeft);
        if (alpha <= ALPHA_FLOOR) {
            return;
        }

        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2;
        int available = graphics.guiWidth() - 2 * MARGIN;

        RenderSystem.enableBlend();

        drawFitted(graphics, minecraft, title(), centerX, centerY, available,
                TITLE_SCALE, TITLE_OFFSET, FastColor.ARGB32.color(alpha, TITLE_COLOR));

        String killer = ClientState.eliminationKiller();
        if (!killer.isEmpty()) {
            drawFitted(graphics, minecraft,
                    Component.translatable("mchelden.elimination.subtitle", killer),
                    centerX, centerY, available, SUBTITLE_SCALE, SUBTITLE_OFFSET,
                    FastColor.ARGB32.color(alpha, SUBTITLE_COLOR));
        }

        RenderSystem.disableBlend();
    }

    /**
     * Die Schluessel kommen aus den Sprachdateien, die Farben aber von hier — wie beim
     * Bounty-Roll. Ein namenloser Ausgeschiedener bekommt einen uebersetzten Platzhalter
     * statt eine Luecke.
     */
    private static Component title() {
        String victim = ClientState.eliminationVictim();
        Component name = victim.isEmpty()
                ? Component.translatable("mchelden.elimination.unknown")
                : Component.literal(victim);
        return Component.translatable("mchelden.elimination.title", name);
    }

    /** Zeichnet eine Zeile mittig, so gross wie moeglich, aber nie ueber den Rand. */
    private static void drawFitted(GuiGraphics graphics, Minecraft minecraft, Component text,
                                   int centerX, int centerY, int available, float maxScale,
                                   int offsetY, int color) {
        int textWidth = minecraft.font.width(text);
        float scale = EliminationAnnouncement.scaleFor(textWidth, available, maxScale);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 0f);
        pose.scale(scale, scale, 1f);
        graphics.drawStringWithBackdrop(minecraft.font, text, -textWidth / 2, offsetY,
                textWidth, color);
        pose.popPose();
    }
}
