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
    /** Wie weit die Brocken fliegen, als Anteil der Bildschirmbreite. */
    private static final float SPREAD_FRACTION = 0.75f;
    private static final float GRAVITY_FRACTION = 0.35f;
    private static final float SPIN_DEGREES = 260f;
    /** Wie klein das Herz zu Beginn des Haltens ist. Es schwillt bis zum Bruch auf. */
    private static final float HOLD_START_SCALE = 0.82f;

    private static final float FLASH_ALPHA = 0.30f;
    private static final float FLASH_PORTION = 0.30f;

    /** Breite der Vignette am Rand, als Anteil der kuerzeren Bildschirmkante. */
    private static final float VIGNETTE_BAND = 0.20f;
    private static final float VIGNETTE_ALPHA = 0.75f;
    private static final int VIGNETTE_COLOR = 0x00280409;
    private static final int VIGNETTE_STEPS = 14;

    /** Wann die Splitter den Rand erreichen — dort schlaegt der blaue Saum an. */
    private static final float RIM_PEAK = 0.34f;
    private static final float RIM_SPAN = 0.26f;
    private static final float RIM_ALPHA = 0.55f;
    private static final int RIM_COLOR = 0x008CC4FF;

    /** Anzahl der kleinen Splitter, die beim Zerplatzen mitfliegen. */
    private static final int SPRAY_COUNT = 46;
    /** Wie weit die Splitter fliegen, als Anteil der grösseren Bildschirmkante. */
    private static final float SPRAY_REACH = 0.62f;
    /** Splitter sind leichter als die Brocken, fliegen also schneller los. */
    private static final float SPRAY_SPEED = 1.5f;
    private static final int[] SPRAY_COLORS = {0x003D8BFD, 0x001E56B4, 0x00C6E2FF};

    private static final float[] JITTER_X = {-0.25f, 0.10f, 0.30f, -0.35f, 0.15f, 0.40f, -0.20f, 0.05f, 0.25f};
    private static final float[] JITTER_Y = {-0.30f, -0.45f, -0.20f, 0.10f, -0.55f, 0.05f, 0.35f, 0.20f, 0.30f};
    private static final float[] SPIN = {-1.0f, 0.6f, 1.3f, -0.7f, 0.2f, 1.0f, -1.2f, 0.8f, -0.4f};

    /** Splitterbahnen, einmalig mit festem Startwert erzeugt: Richtung, Tempo, Groesse, Drehung. */
    private static final float[][] SPRAY = buildSpray();

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
        renderVignette(graphics, width, height, progress);
        renderSpray(graphics, width, height, progress);
        renderShatter(graphics, width, height, progress, partial);
        renderRim(graphics, width, height, progress);
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
     * Dunkle Vignette, die von aussen hereinkriecht.
     *
     * <p>Als gestaffelte Streifen statt als Verlauf gezeichnet: Minecrafts Verlaufsfunktion
     * kann nur senkrecht, und die Vignette braucht alle vier Seiten. Die Streifen ueberlagern
     * sich in den Ecken von selbst, was dort genau die gewuenschte Verdichtung ergibt.
     */
    private static void renderVignette(GuiGraphics graphics, int width, int height, float progress) {
        if (progress <= 0f) {
            return;
        }

        float rise = Mth.clamp(progress / 0.12f, 0f, 1f);
        float fall = Mth.clamp(1f - (progress - 0.30f) / 0.70f, 0f, 1f);
        float strength = rise * fall;
        if (strength <= 0f) {
            return;
        }

        int band = Math.round(Math.min(width, height) * VIGNETTE_BAND);
        int thickness = Math.max(1, band / VIGNETTE_STEPS);

        for (int step = 0; step < VIGNETTE_STEPS; step++) {
            float falloff = 1f - step / (float) VIGNETTE_STEPS;
            int alpha = (int) (falloff * falloff * strength * VIGNETTE_ALPHA * 255f);
            if (alpha <= 0) {
                continue;
            }

            int color = (alpha << 24) | VIGNETTE_COLOR;
            int offset = step * thickness;
            graphics.fill(0, offset, width, offset + thickness, color);
            graphics.fill(0, height - offset - thickness, width, height - offset, color);
            graphics.fill(offset, 0, offset + thickness, height, color);
            graphics.fill(width - offset - thickness, 0, width - offset, height, color);
        }
    }

    /**
     * Blauer Saum am aeussersten Rand — der Moment, in dem die Splitter dort ankommen.
     *
     * <p>Schliesst den Bogen von der Bildmitte bis zur Kante: der Rand leuchtet nicht einfach,
     * er wird getroffen.
     */
    private static void renderRim(GuiGraphics graphics, int width, int height, float progress) {
        float distance = Math.abs(progress - RIM_PEAK);
        if (progress <= 0f || distance > RIM_SPAN) {
            return;
        }

        float strength = 1f - distance / RIM_SPAN;
        int alpha = (int) (strength * strength * RIM_ALPHA * 255f);
        if (alpha <= 0) {
            return;
        }

        int color = (alpha << 24) | RIM_COLOR;
        int thickness = Math.max(2, Math.round(Math.min(width, height) * 0.008f));

        graphics.fill(0, 0, width, thickness, color);
        graphics.fill(0, height - thickness, width, height, color);
        graphics.fill(0, 0, thickness, height, color);
        graphics.fill(width - thickness, 0, width, height, color);
    }

    /**
     * Der Splitterschauer: viele kleine Scherben, die mit den grossen Brocken davonfliegen.
     *
     * <p>Sie starten in derselben Mitte und gehorchen derselben Schwerkraft wie die Brocken,
     * sind aber leichter — sie schiessen weiter voraus und sind vor ihnen verschwunden.
     */
    private static void renderSpray(GuiGraphics graphics, int width, int height, float progress) {
        if (progress <= 0f) {
            return;
        }

        float travel = Math.min(1f, progress * SPRAY_SPEED);
        float alpha = Mth.clamp(1f - progress * 1.25f, 0f, 1f);
        if (alpha <= 0f) {
            return;
        }

        float reach = Math.max(width, height) * SPRAY_REACH;
        float gravity = height * GRAVITY_FRACTION * 0.8f;
        float centerX = width / 2f;
        float centerY = height / 2f;
        int alphaBits = (int) (alpha * 255f) << 24;

        PoseStack pose = graphics.pose();

        for (int index = 0; index < SPRAY_COUNT; index++) {
            float[] shard = SPRAY[index];
            float distance = reach * shard[2] * travel;

            float x = centerX + shard[0] * distance;
            float y = centerY + shard[1] * distance + gravity * travel * travel;
            int size = Math.max(1, Math.round(height * shard[3]));

            pose.pushPose();
            pose.translate(x, y, 0f);
            pose.mulPose(Axis.ZP.rotationDegrees(shard[4] * 360f * travel));
            graphics.fill(-size / 2, -size / 2, size / 2 + 1, size / 2 + 1,
                    alphaBits | SPRAY_COLORS[index % SPRAY_COLORS.length]);
            pose.popPose();
        }
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

    /** Richtung, Tempo, Groesse und Drehung je Splitter. Fester Startwert, damit es reproduzierbar bleibt. */
    private static float[][] buildSpray() {
        RandomSource random = RandomSource.create(0x48454C44L);
        float[][] spray = new float[SPRAY_COUNT][];

        for (int index = 0; index < SPRAY_COUNT; index++) {
            float angle = (float) (Math.PI * 2.0 * index / SPRAY_COUNT)
                    + (random.nextFloat() - 0.5f) * 0.6f;

            spray[index] = new float[] {
                    Mth.cos(angle),
                    Mth.sin(angle),
                    0.45f + random.nextFloat() * 0.55f,
                    0.005f + random.nextFloat() * 0.007f,
                    random.nextFloat() * 2f - 1f,
            };
        }
        return spray;
    }
}
