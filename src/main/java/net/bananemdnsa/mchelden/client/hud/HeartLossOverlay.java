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

/**
 * Der bildschirmfüllende Herzverlust.
 *
 * <p>Bewusst dieselbe Zerlegung wie im HUD, nur riesig: der grosse Moment und das kleine
 * Detail unten sind sichtbar dasselbe Ereignis statt zweier unabhängiger Effekte.
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

    /** Stärke und Länge des roten Aufblitzens am Anfang. */
    private static final float FLASH_ALPHA = 0.30f;
    private static final float FLASH_PORTION = 0.35f;

    private static final float[] JITTER_X = {-0.25f, 0.10f, 0.30f, -0.35f, 0.15f, 0.40f, -0.20f, 0.05f, 0.25f};
    private static final float[] JITTER_Y = {-0.30f, -0.45f, -0.20f, 0.10f, -0.55f, 0.05f, 0.35f, 0.20f, 0.30f};
    private static final float[] SPIN = {-1.0f, 0.6f, 1.3f, -0.7f, 0.2f, 1.0f, -1.2f, 0.8f, -0.4f};

    private HeartLossOverlay() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || !ClientState.isLossOverlayRunning()) {
            return;
        }

        float remaining = ClientState.lossOverlayProgress(delta.getGameTimeDeltaPartialTick(false));
        float progress = 1f - remaining;

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        RenderSystem.enableBlend();
        renderFlash(graphics, width, height, progress);
        renderShatter(graphics, width, height, progress);
        RenderSystem.disableBlend();
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    /** Kurzes rotes Aufblitzen im ersten Drittel. Traegt den Aufschlag, danach ist es weg. */
    private static void renderFlash(GuiGraphics graphics, int width, int height, float progress) {
        if (progress >= FLASH_PORTION) {
            return;
        }

        float strength = 1f - progress / FLASH_PORTION;
        int alpha = (int) (strength * strength * FLASH_ALPHA * 255f);
        if (alpha <= 0) {
            return;
        }
        graphics.fill(0, 0, width, height, (alpha << 24) | 0x00A0140A);
    }

    private static void renderShatter(GuiGraphics graphics, int width, int height, float progress) {
        float scale = height * HEART_HEIGHT_FRACTION / SPRITE;
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

            float restX = (shardCenterX - center) * scale;
            float restY = (shardCenterY - center) * scale;

            float x = width / 2f + restX + dirX * spread * progress;
            float y = height / 2f + restY + dirY * spread * progress + gravity * progress * progress;

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
}
