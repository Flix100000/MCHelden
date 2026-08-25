package net.bananemdnsa.mchelden.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.bananemdnsa.mchelden.MCHelden;
import net.bananemdnsa.mchelden.client.ClientState;
import net.bananemdnsa.mchelden.state.PlayerState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Die Leben-Reihe über der XP-Leiste.
 *
 * <p>Drei Zustände pro Slot, die auf einen Blick unterscheidbar sein müssen:
 * blaues Herz (vorhanden), dunkler Sockel (verloren), gestrichelter Umriss (das
 * Bounty-Herz, das man noch nie hatte).
 */
public final class HeartHud {
    public static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "hearts");

    private static final ResourceLocation CONTAINER =
            ResourceLocation.withDefaultNamespace("hud/heart/container");
    private static final ResourceLocation BLUE =
            ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "hud/heart_blue");
    private static final ResourceLocation BOUNTY_SLOT =
            ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "hud/heart_slot_empty");

    private static final int SPRITE = 9;
    private static final int SPACING = 11;
    /** Abstand über dem unteren Bildschirmrand, oberhalb der Vanilla-Statusleisten. */
    private static final int BOTTOM_OFFSET = 51;
    private static final float BOUNTY_SLOT_ALPHA = 0.35f;

    /** Kantenlänge eines Bruchstücks. Neun Pixel geteilt durch drei ergibt ein 3×3-Raster. */
    private static final int SHARD = 3;
    private static final int SHARDS_PER_AXIS = SPRITE / SHARD;
    /** Wie weit die Bruchstücke am Ende der Animation auseinandergeflogen sind, in Pixeln. */
    private static final float SPREAD = 13f;
    /** Wie weit sie dabei nach unten gezogen werden. */
    private static final float GRAVITY = 11f;
    private static final float SPIN_DEGREES = 200f;

    /**
     * Kleine Unregelmäßigkeit pro Bruchstück, damit das Auseinanderfliegen nicht symmetrisch
     * wirkt. Fest verdrahtet statt zufällig, damit jeder Herzverlust gleich aussieht.
     */
    private static final float[] JITTER_X = {-0.25f, 0.10f, 0.30f, -0.35f, 0.15f, 0.40f, -0.20f, 0.05f, 0.25f};
    private static final float[] JITTER_Y = {-0.30f, -0.45f, -0.20f, 0.10f, -0.55f, 0.05f, 0.35f, 0.20f, 0.30f};
    private static final float[] SPIN = {-1.0f, 0.6f, 1.3f, -0.7f, 0.2f, 1.0f, -1.2f, 0.8f, -0.4f};

    private HeartHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.player.isSpectator()) {
            return;
        }

        int hearts = ClientState.getHearts();
        int slots = PlayerState.MAX_HEARTS;

        int rowWidth = (slots - 1) * SPACING + SPRITE;
        int left = (graphics.guiWidth() - rowWidth) / 2;
        int top = graphics.guiHeight() - BOTTOM_OFFSET;

        RenderSystem.enableBlend();
        for (int slot = 0; slot < slots; slot++) {
            renderSlot(graphics, left + slot * SPACING, top, slot, hearts, delta);
        }
        RenderSystem.disableBlend();
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    private static void renderSlot(GuiGraphics graphics, int x, int y, int slot, int hearts, DeltaTracker delta) {
        if (slot < hearts) {
            graphics.blitSprite(CONTAINER, x, y, SPRITE, SPRITE);
            graphics.blitSprite(BLUE, x, y, SPRITE, SPRITE);
            return;
        }

        // Der Slot, der gerade verloren geht: Sockel steht schon, das Herz zerspringt darüber.
        if (slot == hearts && ClientState.isLossAnimationRunning()) {
            graphics.blitSprite(CONTAINER, x, y, SPRITE, SPRITE);
            renderShatter(graphics, x, y, delta);
            return;
        }

        if (slot < PlayerState.DEFAULT_HEARTS) {
            graphics.blitSprite(CONTAINER, x, y, SPRITE, SPRITE);
            return;
        }

        graphics.setColor(1f, 1f, 1f, BOUNTY_SLOT_ALPHA);
        graphics.blitSprite(BOUNTY_SLOT, x, y, SPRITE, SPRITE);
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    /**
     * Zerlegt das Herz in neun Bruchstücke, die nach außen davonfliegen.
     *
     * <p>Die Flugrichtung ergibt sich aus der Lage des Stücks im Herz — was oben links saß,
     * fliegt nach oben links. Dadurch wirkt es wie ein Zerspringen und nicht wie ein Auffächern.
     */
    private static void renderShatter(GuiGraphics graphics, int x, int y, DeltaTracker delta) {
        float progress = 1f - ClientState.lossAnimationProgress(delta.getGameTimeDeltaPartialTick(false));
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

            float offsetX = dirX * SPREAD * progress;
            float offsetY = dirY * SPREAD * progress + GRAVITY * progress * progress;

            pose.pushPose();
            pose.translate(x + shardCenterX + offsetX, y + shardCenterY + offsetY, 0f);
            pose.mulPose(Axis.ZP.rotationDegrees(SPIN[index] * SPIN_DEGREES * progress));
            pose.translate(-(SHARD / 2f), -(SHARD / 2f), 0f);

            graphics.setColor(1f, 1f, 1f, alpha);
            graphics.blitSprite(BLUE, SPRITE, SPRITE, column * SHARD, row * SHARD, 0, 0, SHARD, SHARD);
            pose.popPose();
        }

        graphics.setColor(1f, 1f, 1f, 1f);
    }
}
