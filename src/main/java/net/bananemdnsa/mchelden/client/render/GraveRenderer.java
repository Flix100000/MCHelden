package net.bananemdnsa.mchelden.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.bananemdnsa.mchelden.grave.GraveBlockEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Der Lichtstrahl über einem frischen Grab.
 *
 * <p>Kurz und vergänglich: hoch genug, um über Bäume zu reichen und ein Rennen zurück
 * auszulösen, aber weder himmelhoch noch dauerhaft. Bei zwanzig Spielern über anderthalb
 * Wochen würde die Map sonst zu einem Wald aus Leuchtsäulen.
 *
 * <p>Statt auszublenden schrumpft der Strahl. Der Vanilla-Strahl gibt keine Deckkraft her,
 * und ein schrumpfender Strahl liest sich ohnehin klarer als ein blasser: man sieht, dass
 * die Zeit läuft.
 */
public class GraveRenderer implements BlockEntityRenderer<GraveBlockEntity> {
    /** Höhe in Blöcken, solange der Strahl auf voller Länge steht. */
    private static final int MAX_HEIGHT = 14;
    /** Wie lange der Strahl insgesamt sichtbar bleibt, in Ticks. Vier Minuten. */
    private static final int LIFETIME_TICKS = 4 * 60 * 20;
    /** Ab wann er zu schrumpfen beginnt. Davor steht er ruhig. */
    private static final float SHRINK_START = 0.35f;

    /** Gedecktes Blaugrau, passend zum Grabstein und klar unterscheidbar von einem Leuchtfeuer. */
    private static final int COLOR = 0xFF8FA8C8;

    private static final float BEAM_RADIUS = 0.10f;
    private static final float GLOW_RADIUS = 0.14f;

    public GraveRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(GraveBlockEntity grave, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (grave.getLevel() == null) {
            return;
        }

        long age = grave.getLevel().getGameTime() - grave.getDiedAt();
        int height = heightFor(age);
        if (height <= 0) {
            return;
        }

        BeaconRenderer.renderBeaconBeam(poseStack, bufferSource, BeaconRenderer.BEAM_LOCATION,
                partialTick, 1.0F, grave.getLevel().getGameTime(), 1, height, COLOR,
                BEAM_RADIUS, GLOW_RADIUS);
    }

    private static int heightFor(long age) {
        if (age < 0 || age >= LIFETIME_TICKS) {
            return 0;
        }

        float progress = age / (float) LIFETIME_TICKS;
        if (progress <= SHRINK_START) {
            return MAX_HEIGHT;
        }

        float remaining = 1f - (progress - SHRINK_START) / (1f - SHRINK_START);
        return Mth.floor(MAX_HEIGHT * remaining);
    }

    /** Der Strahl soll aus der Ferne sichtbar sein, sonst löst er kein Rennen aus. */
    @Override
    public int getViewDistance() {
        return 192;
    }

    @Override
    public boolean shouldRenderOffScreen(GraveBlockEntity grave) {
        return true;
    }

    @Override
    public boolean shouldRender(GraveBlockEntity grave, Vec3 cameraPos) {
        return Vec3.atCenterOf(grave.getBlockPos()).multiply(1.0, 0.0, 1.0)
                .closerThan(cameraPos.multiply(1.0, 0.0, 1.0), getViewDistance());
    }
}
