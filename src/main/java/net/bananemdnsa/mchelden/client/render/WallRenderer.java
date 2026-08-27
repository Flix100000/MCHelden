package net.bananemdnsa.mchelden.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.bananemdnsa.mchelden.client.ClientState;
import net.bananemdnsa.mchelden.world.DividerWall;

import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Zeichnet die Trennwand bei X = 0.
 *
 * <p>Minecraft kann pro Dimension nur eine Worldborder, und die ist von der Weltgrenze
 * belegt. Diese Ebene ist deswegen nachgebaut — nach demselben Verfahren, das Vanilla fuer
 * seine Border benutzt: dieselbe Textur, dasselbe additive Blending, dieselbe wandernde
 * UV-Verschiebung. Nur die Farbe ist eine andere, und es ist eine Ebene statt vier.
 *
 * <p><b>Die Hoehe erledigt sich von selbst.</b> Die Ebene reicht von {@code -depthFar} bis
 * {@code +depthFar} um die Kamera herum — es gibt kein Oben und Unten, an dem sie enden
 * koennte, genau wie bei der Weltgrenze.
 */
public final class WallRenderer {
    private static final ResourceLocation FORCEFIELD =
            ResourceLocation.withDefaultNamespace("textures/misc/forcefield.png");

    /**
     * Bernstein.
     *
     * <p>Die Weltgrenze ist blau, die Final-War-Border wird rot. Drei verschiedene Dinge
     * duerfen nicht gleich aussehen — und ein warmes Gelb sagt nebenbei das Richtige: das
     * hier ist eine Absperrung, und sie faellt irgendwann.
     */
    private static final float RED = 1.0f;
    private static final float GREEN = 0.72f;
    private static final float BLUE = 0.18f;

    /** Wie schnell die Textur wandert. Derselbe Wert wie bei der Vanilla-Border. */
    private static final long SCROLL_PERIOD = 3000L;

    /** Wie hoch die gluehende Schnittkante ist, in Bloecken. */
    private static final float EDGE_HEIGHT = 2.5f;

    private WallRenderer() {
    }

    /**
     * Schreibt in den Chat, was der Client ueber die Wand weiss.
     *
     * <p>Bleibt der Bildschirm leer, gibt es genau vier Gruende: die Wand gilt hier als
     * gefallen, die Kamera ist zu weit weg, die Deckkraft ist auf null gerundet, oder der
     * Z-Ausschnitt ist leer. Diese Zeile unterscheidet sie — ohne sie waere jede Erklaerung
     * geraten.
     */
    public static void report() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        Vec3 eye = minecraft.gameRenderer.getMainCamera().getPosition();
        double range = minecraft.options.getEffectiveRenderDistance() * 16.0;
        double closeness = 1.0 - Math.abs(eye.x) / range;
        double alpha = closeness <= 0.0 ? 0.0 : Mth.clamp(Math.pow(closeness, 4.0), 0.0, 1.0);

        WorldBorder border = minecraft.level.getWorldBorder();
        double from = Math.max(Mth.floor(eye.z - range), border.getMinZ());
        double to = Math.min(Mth.ceil(eye.z + range), border.getMaxZ());

        minecraft.player.sendSystemMessage(Component.literal(String.format(
                "Wand: %s | Abstand %.1f | Reichweite %.0f | Deckkraft %.3f | Z von %.0f bis %.0f | Felder %d",
                ClientState.isWallUp() ? "steht" : "gefallen",
                Math.abs(eye.x), range, alpha, from, to, Math.max(0, (int) (to - from)))));
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || !ClientState.isWallUp()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        double range = minecraft.options.getEffectiveRenderDistance() * 16.0;

        // Genau wie bei der Weltgrenze: erst kurz davor wird sie sichtbar, und dann schnell.
        // Die vierte Potenz sorgt dafuer, dass sie aus der Ferne nicht die Sicht zupflastert.
        double closeness = 1.0 - Math.abs(eye.x) / range;
        if (closeness <= 0.0) {
            return;
        }
        float alpha = (float) Mth.clamp(Math.pow(closeness, 4.0), 0.0, 1.0);

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        draw(minecraft, eye, range, alpha, ClientState.wallEdge(partial));
    }

    private static void draw(Minecraft minecraft, Vec3 eye, double range, float alpha, double edge) {
        WorldBorder border = minecraft.level.getWorldBorder();
        double far = minecraft.gameRenderer.getDepthFar();

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.setShaderTexture(0, FORCEFIELD);
        RenderSystem.depthMask(Minecraft.useShaderTransparency());
        // Je tiefer die Kante steht, desto heisser die Wand: aus Bernstein wird Weissglut.
        float heat = (float) DividerWall.dropHeat(edge);
        RenderSystem.setShaderColor(
                RED,
                GREEN + (1.0f - GREEN) * heat,
                BLUE + (1.0f - BLUE) * heat,
                alpha);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        // Ohne den Versatz streitet die Ebene mit Bloecken, die genau darin stehen.
        RenderSystem.polygonOffset(-3.0f, -3.0f);
        RenderSystem.enablePolygonOffset();

        // Die Wand hat keine Rueckseite: sie muss von beiden Haelften aus sichtbar sein.
        RenderSystem.disableCull();

        // Solange die Kante ueber der Sichtweite liegt, hat die Wand kein Oben — dadurch
        // gibt es beim Beginn des Absinkens keinen sichtbaren Sprung.
        float upper = (float) Math.min(far, edge - eye.y);

        MeshData mesh = buildStrip(eye, range, far, border, upper);
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        if (upper < far) {
            drawSinkingEdge(eye, far, edge, alpha);
        }

        RenderSystem.enableCull();
        RenderSystem.polygonOffset(0.0f, 0.0f);
        RenderSystem.disablePolygonOffset();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.depthMask(true);
    }

    /**
     * Die gluehende Kante, an der die Wand gerade absinkt.
     *
     * <p>Ohne sie sieht man nur, dass die Wand kuerzer wird — nicht, dass dort gerade etwas
     * passiert. Ein paar Bloecke in Weissglut machen daraus eine Schnittkante, die sichtbar
     * nach unten wandert.
     */
    private static void drawSinkingEdge(Vec3 eye, double far, double edge, float alpha) {
        float relative = (float) (edge - eye.y);
        if (relative < -far || relative > far) {
            return;
        }

        RenderSystem.setShaderColor(1.0f, 0.97f, 0.88f, Math.min(1.0f, alpha * 2.2f));

        MeshData mesh = buildBand(eye, far, relative);
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    /** Ein waagerechtes Band direkt unter der Oberkante. */
    private static MeshData buildBand(Vec3 eye, double far, float edge) {
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        float scroll = (float) (Util.getMillis() % SCROLL_PERIOD) / SCROLL_PERIOD;
        float top = (float) -Mth.frac(eye.y * 0.5);
        float x = (float) -eye.x;

        float upper = edge;
        float lower = edge - EDGE_HEIGHT;
        float vUpper = vAt(top, far, upper);
        float vLower = vAt(top, far, lower);

        Minecraft minecraft = Minecraft.getInstance();
        double range = minecraft.options.getEffectiveRenderDistance() * 16.0;
        WorldBorder border = minecraft.level.getWorldBorder();
        double from = Math.max(Mth.floor(eye.z - range), border.getMinZ());
        double to = Math.min(Mth.ceil(eye.z + range), border.getMaxZ());

        float u = (float) (Mth.floor(from) & 1) * 0.5f;
        for (double z = from; z < to; z += 1.0, u += 0.5f) {
            double step = Math.min(1.0, to - z);
            float uStep = (float) step * 0.5f;

            buffer.addVertex(x, lower, (float) (z - eye.z)).setUv(scroll + u, scroll + vLower);
            buffer.addVertex(x, lower, (float) (z + step - eye.z))
                    .setUv(scroll + uStep + u, scroll + vLower);
            buffer.addVertex(x, upper, (float) (z + step - eye.z))
                    .setUv(scroll + uStep + u, scroll + vUpper);
            buffer.addVertex(x, upper, (float) (z - eye.z)).setUv(scroll + u, scroll + vUpper);
        }

        return buffer.build();
    }

    /**
     * Baut die Ebene als Streifen aus Ein-Block-Feldern entlang Z.
     *
     * <p>Feldweise statt als ein grosses Viereck, weil die Textur sonst ueber tausend
     * Bloecke gestreckt wuerde. Gezeichnet wird nur der Ausschnitt um die Kamera herum.
     *
     * <p>Die Oberkante liegt normalerweise ausserhalb der Sichtweite — die Wand hat also
     * kein Oben. Nur waehrend sie absinkt, bekommt sie eins, und das wandert nach unten.
     */
    private static MeshData buildStrip(Vec3 eye, double range, double far, WorldBorder border,
                                       float upper) {
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        float scroll = (float) (Util.getMillis() % SCROLL_PERIOD) / SCROLL_PERIOD;
        float top = (float) -Mth.frac(eye.y * 0.5);
        float vUpper = vAt(top, far, upper);
        float vLower = vAt(top, far, (float) -far);

        double from = Math.max(Mth.floor(eye.z - range), border.getMinZ());
        double to = Math.min(Mth.ceil(eye.z + range), border.getMaxZ());
        float u = (float) (Mth.floor(from) & 1) * 0.5f;

        float x = (float) -eye.x;
        for (double z = from; z < to; z += 1.0, u += 0.5f) {
            double step = Math.min(1.0, to - z);
            float uStep = (float) step * 0.5f;

            buffer.addVertex(x, (float) -far, (float) (z - eye.z)).setUv(scroll + u, scroll + vLower);
            buffer.addVertex(x, (float) -far, (float) (z + step - eye.z))
                    .setUv(scroll + uStep + u, scroll + vLower);
            buffer.addVertex(x, upper, (float) (z + step - eye.z))
                    .setUv(scroll + uStep + u, scroll + vUpper);
            buffer.addVertex(x, upper, (float) (z - eye.z)).setUv(scroll + u, scroll + vUpper);
        }

        return buffer.build();
    }

    /**
     * Die Texturhoehe zu einer Hoehe im Bild.
     *
     * <p>Ein halbes Texturfeld je Block, dieselbe Dichte wie waagerecht — sonst wuerde das
     * Muster gestaucht, sobald die Wand eine Oberkante bekommt.
     */
    private static float vAt(float top, double far, float y) {
        return top + (float) ((far - y) * 0.5);
    }
}
