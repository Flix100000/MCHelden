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
import net.bananemdnsa.mchelden.phase.PhaseManager;
import net.bananemdnsa.mchelden.world.SafeZone;

import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Zeichnet die Safezone als runde Wand um 0,0.
 *
 * <p>Dieselbe Machart wie die Trennwand und die Weltgrenze: {@code forcefield.png},
 * additives Mischen, wandernde Textur. Nur laeuft die Flaeche hier im Kreis statt gerade.
 *
 * <p><b>Ein Zylinder, keine Kuppel.</b> Die Zone hat kein Oben und kein Unten — aus
 * demselben Grund wie die Trennwand: wer darin einen Turm baut oder in einen Keller graebt,
 * soll nicht ploetzlich draussen stehen. Die Wand reicht deswegen von {@code -depthFar} bis
 * {@code +depthFar} um die Kamera.
 *
 * <p><b>Sichtbar wird sie erst in der Naehe</b>, und dann schnell — genau wie die
 * Weltgrenze. Gemessen wird der waagerechte Abstand zur <em>Wand</em>, nicht zur Mitte:
 * sonst waere sie von innen unsichtbar, obwohl man direkt davorsteht.
 */
public final class SafeZoneRenderer {
    private static final ResourceLocation FORCEFIELD =
            ResourceLocation.withDefaultNamespace("textures/misc/forcefield.png");

    /**
     * Tuerkis-Gruen.
     *
     * <p>Weltgrenze blau, Trennwand bernstein, Final-War-Border rot — und gruen sagt ohne
     * Erklaerung, worum es hier geht.
     */
    private static final float RED = 0.28f;
    private static final float GREEN = 1.0f;
    private static final float BLUE = 0.72f;

    /** Wie schnell die Textur wandert. Derselbe Wert wie bei der Vanilla-Border. */
    private static final long SCROLL_PERIOD = 3000L;

    /** Wie fein der Kreis unterteilt wird. Bei Radius 50 sind das gut fuenf Bloecke je Stueck. */
    private static final int SEGMENTS = 64;

    /** Ein halbes Texturfeld je Block, dieselbe Dichte wie bei der Trennwand. */
    private static final float TEXTURE_DENSITY = 0.5f;

    private SafeZoneRenderer() {
    }

    /**
     * Schreibt in den Chat, was der Client ueber die Safezone weiss.
     *
     * <p>Bleibt sie unsichtbar, gibt es genau zwei Gruende: sie gilt hier als abgeschaltet,
     * oder die Kamera ist zu weit von der Wand weg. Diese Zeile unterscheidet sie.
     */
    public static void report() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        Vec3 eye = minecraft.gameRenderer.getMainCamera().getPosition();
        double toAxis = Math.sqrt(eye.x * eye.x + eye.z * eye.z);
        double toWall = Math.abs(toAxis - SafeZone.RADIUS);

        double range = minecraft.options.getEffectiveRenderDistance() * 16.0;
        double closeness = 1.0 - toWall / range;
        double alpha = closeness <= 0.0 ? 0.0 : Mth.clamp(Math.pow(closeness, 4.0), 0.0, 1.0);

        minecraft.player.sendSystemMessage(Component.literal(String.format(
                "Safezone: %s | Abstand zur Achse %.1f | zur Wand %.1f | Deckkraft %.3f | %s",
                SafeZone.isActive(minecraft.level) ? "aktiv" : "aus",
                toAxis, toWall, alpha,
                toAxis <= SafeZone.RADIUS ? "du bist drin" : "du bist draussen")));

        // Der Bruch ist der Teil, den nur der Client kennt. Bleibt der Bildschirm leer,
        // unterscheidet diese Zeile die Ursachen: kein Paket angekommen, Uhr schon
        // abgelaufen, oder Zone abgeschaltet ohne dass ein Bruch lief.
        minecraft.player.sendSystemMessage(Component.literal(String.format(
                "Kuppel: Aufziehen %s | Bruch %s | Gluehen %.2f",
                ticksOrDash(ClientState.safeZoneArmTicks()),
                ticksOrDash(ClientState.safeZoneShatterTicks()),
                armGlow())));
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        int shatterTicks = ClientState.safeZoneShatterTicks();

        // Waehrend des Bruchs gilt die Zone schon als abgeschaltet — der Schutz endet mit
        // dem Bruch, nicht mit der letzten Scherbe. Gezeichnet wird trotzdem weiter.
        if (!SafeZone.isActive(minecraft.level) && shatterTicks < 0) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();

        // Abstand zur Wand, nicht zur Mitte: von innen wie von aussen gleich.
        double toAxis = Math.sqrt(eye.x * eye.x + eye.z * eye.z);
        double toWall = Math.abs(toAxis - SafeZone.RADIUS);

        double range = minecraft.options.getEffectiveRenderDistance() * 16.0;
        double closeness = 1.0 - toWall / range;
        if (closeness <= 0.0) {
            return;
        }

        draw(minecraft, eye, (float) Mth.clamp(Math.pow(closeness, 4.0), 0.0, 1.0),
                shatterTicks, event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    private static void draw(Minecraft minecraft, Vec3 eye, float alpha,
                             int shatterTicks, float partial) {
        double far = minecraft.gameRenderer.getDepthFar();

        // Beim Aufziehen gluecht die Wand von tuerkis auf weiss — dieselbe Machart wie beim
        // Absinken der Trennwand. Sie sagt an, was gleich passiert.
        float glow = armGlow();

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.setShaderTexture(0, FORCEFIELD);
        RenderSystem.depthMask(Minecraft.useShaderTransparency());
        RenderSystem.setShaderColor(
                Mth.lerp(glow, RED, 1.0f), GREEN, Mth.lerp(glow, BLUE, 1.0f), alpha);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        RenderSystem.polygonOffset(-3.0f, -3.0f);
        RenderSystem.enablePolygonOffset();

        // Von innen wie von aussen sichtbar: drinnen steht man, wenn man verhandelt,
        // draussen, wenn man zuschaut.
        RenderSystem.disableCull();

        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        if (shatterTicks >= 0) {
            SafeZoneShatter.build(buffer, eye, shatterTicks, partial);
        } else {
            buildRing(buffer, eye, far);
        }

        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        RenderSystem.enableCull();
        RenderSystem.polygonOffset(0.0f, 0.0f);
        RenderSystem.disablePolygonOffset();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.depthMask(true);
    }

    /** {@code -1} heisst: laeuft gerade nicht. Als Strich liest sich das schneller. */
    private static String ticksOrDash(int ticks) {
        return ticks < 0 ? "—" : ticks + " Ticks";
    }

    /** Wie weit die Wand ins Weisse gluecht: 0 im Ruhezustand, 1 im Moment vor dem Bruch. */
    private static float armGlow() {
        int arm = ClientState.safeZoneArmTicks();
        if (arm < 0) {
            return 0.0f;
        }
        return Mth.clamp(arm / (float) (PhaseManager.FINAL_WAR_SECONDS * 20), 0.0f, 1.0f);
    }

    /**
     * Baut die runde Wand, kameranah gerechnet.
     *
     * <p>Die Texturkoordinaten folgen der Bogenlaenge, nicht dem Winkel — sonst waere das
     * Muster bei einem groesseren Radius gedehnt. Waagerecht und senkrecht dieselbe Dichte
     * wie bei der Trennwand, damit die drei Flaechen im Spiel zusammenpassen.
     */
    private static void buildRing(BufferBuilder buffer, Vec3 eye, double far) {
        float scroll = (float) (Util.getMillis() % SCROLL_PERIOD) / SCROLL_PERIOD;
        float top = (float) -Mth.frac(eye.y * 0.5);
        float bottom = top + (float) far;

        for (int segment = 0; segment < SEGMENTS; segment++) {
            float left = angle(segment);
            float right = angle(segment + 1);

            float leftX = offsetX(eye, left);
            float leftZ = offsetZ(eye, left);
            float rightX = offsetX(eye, right);
            float rightZ = offsetZ(eye, right);

            float uLeft = scroll + left * (float) SafeZone.RADIUS * TEXTURE_DENSITY;
            float uRight = scroll + right * (float) SafeZone.RADIUS * TEXTURE_DENSITY;

            buffer.addVertex(leftX, (float) -far, leftZ).setUv(uLeft, scroll + bottom);
            buffer.addVertex(rightX, (float) -far, rightZ).setUv(uRight, scroll + bottom);
            buffer.addVertex(rightX, (float) far, rightZ).setUv(uRight, scroll + top);
            buffer.addVertex(leftX, (float) far, leftZ).setUv(uLeft, scroll + top);
        }
    }

    private static float angle(int segment) {
        return (float) (2.0 * Math.PI * segment / SEGMENTS);
    }

    private static float offsetX(Vec3 eye, float angle) {
        return (float) (Mth.cos(angle) * SafeZone.RADIUS - eye.x);
    }

    private static float offsetZ(Vec3 eye, float angle) {
        return (float) (Mth.sin(angle) * SafeZone.RADIUS - eye.z);
    }
}
