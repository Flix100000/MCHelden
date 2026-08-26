package net.bananemdnsa.mchelden.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.bananemdnsa.mchelden.grave.GraveBlock;
import net.bananemdnsa.mchelden.grave.GraveBlockEntity;

import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
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

    /**
     * Kraeftiges Blau, passend zu den Herzen.
     *
     * <p>Der erste Versuch war ein gedecktes Blaugrau — im Strahl kam davon nichts an, weil
     * die Textur ohnehin hell ist und ein heller Farbton darauf schlicht weiss ergibt.
     */
    private static final int COLOR = 0xFF2A6BD0;


    /** Schwebehoehe des Kopfes ueber dem Block, plus Ausschlag und Tempo des Wippens. */
    private static final float HEAD_HEIGHT = 1.05f;
    private static final float BOB_HEIGHT = 0.055f;
    private static final float BOB_SPEED = 0.06f;

    /** Ab wieviel Bloecken Entfernung das Namensschild erscheint. */
    private static final double NAMEPLATE_RANGE = 12.0;
    private static final double NAMEPLATE_RANGE_SQR = NAMEPLATE_RANGE * NAMEPLATE_RANGE;

    private final Font font;
    private final ItemRenderer itemRenderer;

    public GraveRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
    }

    @Override
    public void render(GraveBlockEntity grave, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (grave.getLevel() == null) {
            return;
        }

        float height = heightFor(grave.getLevel().getGameTime() - grave.getDiedAt());

        GraveBeam.render(poseStack, bufferSource, partialTick,
                grave.getLevel().getGameTime(), height, COLOR);

        renderHead(grave, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        renderNameplate(grave, poseStack, bufferSource, packedLight);
    }

    /** Der Kopf des Toten schwebt über dem Grabstein und wippt langsam auf und ab. */
    private void renderHead(GraveBlockEntity grave, float partialTick, PoseStack poseStack,
                            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ItemStack head = grave.getHeadStack();
        if (head.isEmpty() || grave.getLevel() == null) {
            return;
        }

        float time = grave.getLevel().getGameTime() + partialTick;
        float bob = Mth.sin(time * BOB_SPEED) * BOB_HEIGHT;

        poseStack.pushPose();
        poseStack.translate(0.5, HEAD_HEIGHT + bob, 0.5);
        poseStack.scale(0.62f, 0.62f, 0.62f);
        poseStack.mulPose(Axis.YP.rotationDegrees(
                -grave.getBlockState().getValue(GraveBlock.FACING).toYRot()));

        itemRenderer.renderStatic(head, ItemDisplayContext.FIXED, packedLight, packedOverlay,
                poseStack, bufferSource, grave.getLevel(), 0);
        poseStack.popPose();
    }

    /**
     * Name und Todeszeitpunkt, nur aus der Naehe.
     *
     * <p>Dauerhaft sichtbare Schilder ueber jedem Grab wuerden die Landschaft zupflastern.
     * Wer davorsteht, will wissen wessen Grab es ist; wer zweihundert Bloecke weg steht,
     * braucht die Information nicht.
     */
    private void renderNameplate(GraveBlockEntity grave, PoseStack poseStack,
                                 MultiBufferSource bufferSource, int packedLight) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 cameraPos = minecraft.gameRenderer.getMainCamera().getPosition();

        if (Vec3.atCenterOf(grave.getBlockPos()).distanceToSqr(cameraPos) > NAMEPLATE_RANGE_SQR) {
            return;
        }

        // Auch ohne Namen zeichnen: dann steht wenigstens die Zeit da, und man sieht am
        // Ergebnis ob die Darstellung laeuft oder die Daten fehlen.
        Component name = grave.getOwnerName().isEmpty()
                ? Component.translatable("mchelden.grave.unknown")
                : Component.literal(grave.getOwnerName());
        Component since = elapsedSince(grave);

        poseStack.pushPose();
        poseStack.translate(0.5, 1.62, 0.5);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.02f, -0.02f, 0.02f);

        // Volle Helligkeit: sonst ist das Schild in einer dunklen Ecke kaum zu lesen.
        drawCentered(name, 0, 0xFFFFFFFF, poseStack, bufferSource, LightTexture.FULL_BRIGHT);
        drawCentered(since, 10, 0xFFC0C0C0, poseStack, bufferSource, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    private void drawCentered(Component text, int y, int color, PoseStack poseStack,
                              MultiBufferSource bufferSource, int packedLight) {
        // POLYGON_OFFSET statt NORMAL: das ist der Modus, mit dem Minecraft selbst Text in
        // einem Blockentity zeichnet (Schilder). NORMAL gehoert zum Entitaeten-Durchgang und
        // wird in diesem Durchgang nicht zuverlaessig ausgegeben.
        font.drawInBatch(text, -font.width(text) / 2f, y, color, false,
                poseStack.last().pose(), bufferSource, Font.DisplayMode.POLYGON_OFFSET, 0, packedLight);
    }

    /** Wie lange der Tod her ist, in ganzen Minuten. */
    private static Component elapsedSince(GraveBlockEntity grave) {
        long ticks = grave.getLevel() == null ? 0 : grave.getLevel().getGameTime() - grave.getDiedAt();
        int minutes = (int) Math.max(0, ticks / (20 * 60));

        return minutes < 1
                ? Component.translatable("mchelden.grave.just_now")
                : Component.translatable("mchelden.grave.minutes_ago", minutes);
    }

    private static float heightFor(long age) {
        if (age < 0 || age >= LIFETIME_TICKS) {
            return 0f;
        }

        float progress = age / (float) LIFETIME_TICKS;
        if (progress <= SHRINK_START) {
            return MAX_HEIGHT;
        }

        float remaining = 1f - (progress - SHRINK_START) / (1f - SHRINK_START);
        return MAX_HEIGHT * remaining;
    }

    /**
     * Der Bereich, den die Sichtkulissenpruefung heranzieht.
     *
     * <p>NeoForge prueft die Kulisse auch bei Blockentities, die ausserhalb des Bildes
     * gezeichnet werden sollen — anders als Vanilla. Ohne diese Erweiterung zaehlt nur der
     * eine Block, und der vierzehn Bloecke hohe Strahl verschwindet, sobald der Grabstein
     * selbst aus dem Bild faellt.
     */
    @Override
    public AABB getRenderBoundingBox(GraveBlockEntity grave) {
        BlockPos pos = grave.getBlockPos();
        return new AABB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + MAX_HEIGHT + 2.0, pos.getZ() + 1.0);
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
