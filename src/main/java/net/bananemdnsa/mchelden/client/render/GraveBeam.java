package net.bananemdnsa.mchelden.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * Der Lichtstrahl über einem Grab, selbst gezeichnet.
 *
 * <p>Minecrafts Leuchtfeuer-Strahl war der naheliegende Weg, taugt hier aber nicht: seine
 * äussere Hülle erzwingt eine feste Deckkraft und ignoriert die übergebene Farbe in diesem
 * Punkt. Dadurch liess sich der Strahl nach oben nicht ausblenden — er hörte immer hart auf,
 * egal was der innere Kern machte.
 *
 * <p>Hier laufen die Deckkraftwerte direkt in die Eckpunkte. Das ergibt einen echten Verlauf
 * statt gestapelter Abschnitte, und der Strahl verläuft sich nach oben, statt abgeschnitten
 * zu wirken.
 */
public final class GraveBeam {
    /** Wie viele Ringe der Verlauf hat. Mehr Ringe, weicherer Übergang. */
    private static final int STEPS = 12;
    /** Welcher Anteil unten voll deckend bleibt, bevor das Ausgehen beginnt. */
    private static final float SOLID_PORTION = 0.35f;

    private static final float INNER_RADIUS = 0.11f;
    private static final float OUTER_RADIUS = 0.19f;
    /** Deckkraft der weichen Hülle im Verhältnis zum Kern. */
    private static final float GLOW_ALPHA = 0.35f;

    private GraveBeam() {
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource,
                              float partialTick, long gameTime, float height, int color) {
        if (height <= 0f) {
            return;
        }

        // Dieselbe langsame Texturwanderung wie beim Leuchtfeuer, damit sich der Strahl bewegt.
        float scroll = -(Math.floorMod(gameTime, 40L) + partialTick) / 40f;

        VertexConsumer consumer = bufferSource.getBuffer(
                RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, true));

        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);

        column(poseStack, consumer, height, INNER_RADIUS, color, 1f, scroll);
        column(poseStack, consumer, height, OUTER_RADIUS, color, GLOW_ALPHA, scroll);

        poseStack.popPose();
    }

    /** Eine vierseitige Säule, ringweise von unten nach oben durchsichtiger werdend. */
    private static void column(PoseStack poseStack, VertexConsumer consumer, float height,
                               float radius, int color, float baseAlpha, float scroll) {
        PoseStack.Pose pose = poseStack.last();

        for (int step = 0; step < STEPS; step++) {
            float bottom = height * step / STEPS;
            float top = height * (step + 1) / STEPS;

            int alphaBottom = alphaAt(bottom / height, baseAlpha);
            int alphaTop = alphaAt(top / height, baseAlpha);
            if (alphaBottom <= 0 && alphaTop <= 0) {
                continue;
            }

            float vBottom = bottom + scroll;
            float vTop = top + scroll;

            face(pose, consumer, color, alphaBottom, alphaTop, bottom, top,
                    -radius, -radius, radius, -radius, vBottom, vTop);
            face(pose, consumer, color, alphaBottom, alphaTop, bottom, top,
                    radius, -radius, radius, radius, vBottom, vTop);
            face(pose, consumer, color, alphaBottom, alphaTop, bottom, top,
                    radius, radius, -radius, radius, vBottom, vTop);
            face(pose, consumer, color, alphaBottom, alphaTop, bottom, top,
                    -radius, radius, -radius, -radius, vBottom, vTop);
        }
    }

    /**
     * Deckkraft an einer bestimmten Höhe.
     *
     * <p>Unten bleibt es voll, darüber fällt es quadratisch ab — linear sähe der Übergang
     * aus wie eine schräge Kante statt wie ein Verlaufen.
     */
    private static int alphaAt(float portion, float baseAlpha) {
        if (portion <= SOLID_PORTION) {
            return Math.round(255 * baseAlpha);
        }

        float remaining = 1f - (portion - SOLID_PORTION) / (1f - SOLID_PORTION);
        return Math.round(255 * baseAlpha * Mth.clamp(remaining * remaining, 0f, 1f));
    }

    private static void face(PoseStack.Pose pose, VertexConsumer consumer, int color,
                             int alphaBottom, int alphaTop, float bottom, float top,
                             float x1, float z1, float x2, float z2, float vBottom, float vTop) {
        vertex(pose, consumer, color, alphaTop, x1, top, z1, 1f, vTop);
        vertex(pose, consumer, color, alphaBottom, x1, bottom, z1, 1f, vBottom);
        vertex(pose, consumer, color, alphaBottom, x2, bottom, z2, 0f, vBottom);
        vertex(pose, consumer, color, alphaTop, x2, top, z2, 0f, vTop);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, int color, int alpha,
                               float x, float y, float z, float u, float v) {
        consumer.addVertex(pose, x, y, z)
                .setColor((alpha << 24) | (color & 0x00FFFFFF))
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(pose, 0f, 1f, 0f);
    }
}
