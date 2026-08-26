package net.bananemdnsa.mchelden.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.bananemdnsa.mchelden.MCHelden;
import net.bananemdnsa.mchelden.client.ClientState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Der bildschirmfüllende Herzverlust.
 *
 * <p>Bewusst dieselbe Zerlegung wie im HUD und dieselbe Uhr: der grosse Moment und das
 * kleine Detail unten sind sichtbar dasselbe Ereignis statt zweier unabhängiger Effekte.
 */
public final class HeartLossOverlay {
    public static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "heart_loss_overlay");

    private static final ResourceLocation BLUE =
            ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "hud/heart_blue");

    private static final int SPRITE = 9;
    private static final int SHARD = 3;
    private static final int SHARDS_PER_AXIS = SPRITE / SHARD;

    /** Kantenlänge des grossen Herzens, als Anteil der Bildschirmhöhe. */
    private static final float HEART_HEIGHT_FRACTION = 0.34f;
    /** Wie weit die Bruchstücke fliegen, als Anteil der Bildschirmbreite. */
    private static final float SPREAD_FRACTION = 0.75f;
    private static final float GRAVITY_FRACTION = 0.35f;
    private static final float SPIN_DEGREES = 260f;
    /** Wie klein das Herz zu Beginn des Haltens ist. Es schwillt bis zum Bruch auf. */
    private static final float HOLD_START_SCALE = 0.82f;

    private static final float FLASH_ALPHA = 0.30f;
    private static final float FLASH_PORTION = 0.30f;

    private static final int CRACK_COUNT = 9;
    private static final int CRACK_POINTS = 6;
    /** Wie weit die Risse reichen, als Anteil der grösseren Bildschirmkante. */
    private static final float CRACK_REACH = 0.80f;
    /** Risse rasen schneller nach aussen als die Bruchstücke fliegen. */
    private static final float CRACK_SPEED = 2.4f;
    private static final int CRACK_COLOR = 0x00D2E8FF;

    private static final float[] JITTER_X = {-0.25f, 0.10f, 0.30f, -0.35f, 0.15f, 0.40f, -0.20f, 0.05f, 0.25f};
    private static final float[] JITTER_Y = {-0.30f, -0.45f, -0.20f, 0.10f, -0.55f, 0.05f, 0.35f, 0.20f, 0.30f};
    private static final float[] SPIN = {-1.0f, 0.6f, 1.3f, -0.7f, 0.2f, 1.0f, -1.2f, 0.8f, -0.4f};

    /** Risspfade in normierten Einheiten um die Bildmitte, einmalig mit festem Startwert erzeugt. */
    private static final float[][] CRACKS = buildCracks();

    private HeartLossOverlay() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || !ClientState.isLossRunning()) {
            return;
        }

        float partial = delta.getGameTimeDeltaPartialTick(false);
        float progress = ClientState.lossShatterProgress(partial);

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        RenderSystem.enableBlend();
        renderFlash(graphics, width, height, progress);
        renderCracks(graphics, width, height, progress);
        renderShatter(graphics, width, height, progress, partial);
        RenderSystem.disableBlend();
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    /** Kurzes rotes Aufblitzen im Moment des Bruchs. */
    private static void renderFlash(GuiGraphics graphics, int width, int height, float progress) {
        if (progress <= 0f || progress >= FLASH_PORTION) {
            return;
        }

        float strength = 1f - progress / FLASH_PORTION;
        int alpha = (int) (strength * strength * FLASH_ALPHA * 255f);
        if (alpha > 0) {
            graphics.fill(0, 0, width, height, (alpha << 24) | 0x00A0140A);
        }
    }

    /**
     * Sprünge, die von der Bildmitte nach aussen rasen.
     *
     * <p>Die Pfade sind prozedural erzeugt statt gezeichnet: dadurch reichen sie bei jeder
     * Auflösung bis in die Ecken, statt auf ein Seitenverhältnis festgelegt zu sein.
     */
    private static void renderCracks(GuiGraphics graphics, int width, int height, float progress) {
        if (progress <= 0f) {
            return;
        }

        float reveal = Math.min(1f, progress * CRACK_SPEED);
        float alpha = Mth.clamp(1f - progress * 1.4f, 0f, 1f);
        if (alpha <= 0f) {
            return;
        }

        int color = ((int) (alpha * 255f) << 24) | CRACK_COLOR;
        float scale = Math.max(width, height) * CRACK_REACH;
        float centerX = width / 2f;
        float centerY = height / 2f;

        PoseStack pose = graphics.pose();

        for (float[] crack : CRACKS) {
            float previousX = centerX;
            float previousY = centerY;

            for (int point = 0; point < CRACK_POINTS; point++) {
                float pointReveal = (point + 1f) / CRACK_POINTS;
                if (reveal < pointReveal - 1f / CRACK_POINTS) {
                    break;
                }

                float targetX = centerX + crack[point * 2] * scale;
                float targetY = centerY + crack[point * 2 + 1] * scale;

                // Der zuletzt sichtbare Abschnitt wächst noch, statt ruckartig zu erscheinen.
                float portion = Mth.clamp((reveal - (pointReveal - 1f / CRACK_POINTS)) * CRACK_POINTS, 0f, 1f);
                float endX = previousX + (targetX - previousX) * portion;
                float endY = previousY + (targetY - previousY) * portion;

                int thickness = point <= 1 ? 1 : 0;
                drawSegment(graphics, pose, previousX, previousY, endX, endY, thickness, color);

                previousX = targetX;
                previousY = targetY;
            }
        }
    }

    /** Zeichnet eine gedrehte dünne Fläche als Liniensegment. */
    private static void drawSegment(GuiGraphics graphics, PoseStack pose,
                                    float x1, float y1, float x2, float y2, int thickness, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = Mth.sqrt(dx * dx + dy * dy);
        if (length < 0.5f) {
            return;
        }

        pose.pushPose();
        pose.translate(x1, y1, 0f);
        pose.mulPose(Axis.ZP.rotationDegrees((float) Math.toDegrees(Math.atan2(dy, dx))));
        graphics.fill(0, -thickness, Math.round(length), thickness + 1, color);
        pose.popPose();
    }

    private static void renderShatter(GuiGraphics graphics, int width, int height,
                                      float progress, float partial) {
        // Waehrend des Haltens schwillt das Herz auf und zittert, danach zerspringt es.
        float hold = ClientState.lossHoldProgress(partial);
        float growth = progress > 0f ? 1f : Mth.lerp(hold, HOLD_START_SCALE, 1f);
        float shiver = progress > 0f ? 0f
                : Mth.sin(ClientState.lossElapsedTicks(partial) * 2.2f) * hold * height * 0.004f;

        float scale = height * HEART_HEIGHT_FRACTION / SPRITE * growth;
        float spread = width * SPREAD_FRACTION;
        float gravity = height * GRAVITY_FRACTION;
        float alpha = Math.max(0f, 1f - progress * progress);
        float center = SPRITE / 2f;

        PoseStack pose = graphics.pose();

        for (int index = 0; index < SHARDS_PER_AXIS * SHARDS_PER_AXIS; index++) {
            int column = index % SHARDS_PER_AXIS;
            int row = index / SHARDS_PER_AXIS;

            float shardCenterX = column * SHARD + SHARD / 2f;
            float shardCenterY = row * SHARD + SHARD / 2f;

            float dirX = (shardCenterX - center) / center + JITTER_X[index];
            float dirY = (shardCenterY - center) / center + JITTER_Y[index];

            float x = width / 2f + (shardCenterX - center) * scale + dirX * spread * progress + shiver;
            float y = height / 2f + (shardCenterY - center) * scale + dirY * spread * progress
                    + gravity * progress * progress;

            pose.pushPose();
            pose.translate(x, y, 0f);
            pose.mulPose(Axis.ZP.rotationDegrees(SPIN[index] * SPIN_DEGREES * progress));
            pose.scale(scale, scale, 1f);
            pose.translate(-(SHARD / 2f), -(SHARD / 2f), 0f);

            graphics.setColor(1f, 1f, 1f, alpha);
            graphics.blitSprite(BLUE, SPRITE, SPRITE, column * SHARD, row * SHARD, 0, 0, SHARD, SHARD);
            pose.popPose();
        }
    }

    private static float[][] buildCracks() {
        RandomSource random = RandomSource.create(0x48454C44L);
        float[][] cracks = new float[CRACK_COUNT][];

        for (int crack = 0; crack < CRACK_COUNT; crack++) {
            float angle = (float) (Math.PI * 2.0 * crack / CRACK_COUNT)
                    + (random.nextFloat() - 0.5f) * 0.45f;

            float[] path = new float[CRACK_POINTS * 2];
            float x = 0f;
            float y = 0f;

            for (int point = 0; point < CRACK_POINTS; point++) {
                angle += (random.nextFloat() - 0.5f) * 0.55f;
                float step = 0.09f + random.nextFloat() * 0.07f;
                x += Mth.cos(angle) * step;
                y += Mth.sin(angle) * step;
                path[point * 2] = x;
                path[point * 2 + 1] = y;
            }
            cracks[crack] = path;
        }
        return cracks;
    }
}
