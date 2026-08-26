package net.bananemdnsa.mchelden.client.hud;

import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.bananemdnsa.mchelden.MCHelden;
import net.bananemdnsa.mchelden.bounty.BountyRollTiming;
import net.bananemdnsa.mchelden.client.BountyRoll;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Das Gluecksrad der Bounty-Auslosung: ein waagerechtes Band ueber die volle Breite, durch
 * das Koepfe von rechts nach links ziehen, ausrollen und unter einem festen Zeiger einrasten.
 *
 * <p>Ein wechselndes Bild in der HUD-Ecke laesst niemanden mitfiebern. Erst dadurch, dass
 * man die Koepfe vorbeiziehen und langsamer werden sieht, entsteht der Moment, um den es
 * hier geht — der Roll passiert im ganzen Projekt genau einmal.
 *
 * <p>Vier Mittel tragen die Spannung, und alle vier haengen am Tempo des Streifens statt an
 * einer eigenen Uhr: der <em>Spotlight</em> zieht sich zu, die <em>Kacheln wachsen</em>, die
 * <em>Vignette</em> schwillt an, und ganz zum Schluss <em>ruckt</em> das Band. Der
 * Fast-Stopp selbst steckt in {@link BountyRoll} — er ist Bewegung, nicht Darstellung.
 *
 * <p>Das Band liegt im oberen Bildschirmdrittel und nicht in der Mitte: waehrend der elf
 * Sekunden soll niemand blind sein. Und es dunkelt nur die Raender ab, nicht das Bild.
 */
public final class BountyRollOverlay {
    public static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "bounty_roll");

    /** Grundgroesse einer Kachel. Alles Weitere ist ein Faktor darauf. */
    private static final int TILE = 32;
    /**
     * Wie weit Kacheln und Band im Stillstand aufgehen.
     *
     * <p>Ein Faktor, keine zweite Pixelgroesse: Kachelgroesse und Abstand muessen in
     * Fliesskomma wachsen. Waren sie ganzzahlig, sprang der Abstand irgendwann von 40 auf
     * 41 Pixel — und eine Kachel fuenf Plaetze weiter aussen machte in dem Moment einen
     * Satz von fuenf Pixeln. Genau das war das Ruckeln.
     */
    private static final float ZOOM_SLOW = 1.32f;
    private static final int GAP = 8;
    private static final int PADDING = 8;
    private static final int BORDER = 1;
    /** Wie weit das Band ueber die Bildschirmkanten hinausgeht, damit der Ruck keine Luecke reisst. */
    private static final int BLEED = 8;

    /** Hoehe der Bandmitte, als Anteil der Bildschirmhoehe. */
    private static final float BAND_POSITION = 0.30f;
    /** Wie breit die Koepfe an den Raendern ins Dunkle auslaufen. */
    private static final int FADE_WIDTH = 96;

    private static final int FRAME = 0xFF000000;
    private static final int TRACK = 0xE60A0F16;
    /** Herzblau — die Farbe dessen, was am Ende dabei herausspringt. */
    private static final int ACCENT = 0xFF5AA9F0;
    private static final int MARKER_BOX = 0x705AA9F0;
    private static final int UNKNOWN = 0xFFCBD5DF;
    private static final int NAME = 0xFFFFFFFF;

    /** Wie gross die Ansage hoechstens wird, und wieviel Luft sie zu den Kanten haelt. */
    private static final float TITLE_SCALE = 3f;
    private static final float SUBTITLE_SCALE = 1.4f;
    private static final int TEXT_MARGIN = 24;
    /** Wie lange die Ansage aufspringt und wie lange sie wieder verschwindet. */
    private static final int TITLE_IN = 6;
    private static final int TITLE_OUT = 8;

    private static final int POINTER_HEIGHT = 6;
    /** Wie weit der Gewinnerkopf beim Einrasten aufgeht. */
    private static final float SNAP_PULSE = 0.40f;
    private static final int NAME_GAP = 6;
    /** Wie gross der Name am Ende steht. */
    private static final float NAME_SCALE = 1.6f;

    /** Wie stark die Nachbarkacheln im Stillstand abdunkeln. */
    private static final float SPOTLIGHT_DEPTH = 0.82f;
    /** Ueber wie viele Kacheln der Spotlight ausleuchtet. */
    private static final float SPOTLIGHT_SPREAD = 3f;

    /** Hoehe der Vignette ueber und unter dem Band, als Anteil der Bildschirmhoehe. */
    private static final float VIGNETTE_REACH = 0.55f;
    private static final int VIGNETTE_MAX_ALPHA = 0x7A;

    private BountyRollOverlay() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || !BountyRoll.isRunning()) {
            return;
        }

        float partial = delta.getGameTimeDeltaPartialTick(false);
        float elapsed = BountyRoll.elapsed(partial);
        float speed = BountyRoll.speed(partial);
        float open = openFactor(elapsed);

        int width = graphics.guiWidth();
        int centerX = width / 2 + Math.round(BountyRoll.shakeX(partial));
        int bandCenterY = Math.round(graphics.guiHeight() * BAND_POSITION);

        // Erst die Ansage, allein auf dem Bild. Sie sitzt dort, wo gleich das Band aufgeht.
        if (elapsed < BountyRollTiming.TITLE_END) {
            RenderSystem.enableBlend();
            drawAnnouncement(graphics, minecraft, elapsed, centerX, bandCenterY, width);
            RenderSystem.disableBlend();
            return;
        }

        // Was langsamer wird, kommt naeher: die Kacheln wachsen mit dem Ausrollen, das Band
        // waechst mit ihnen. Der Blick wird dadurch hineingezogen, ohne dass sich etwas bewegt.
        float zoom = Mth.lerp(speed, ZOOM_SLOW, 1f);
        float tileSize = TILE * zoom;
        float pitch = (TILE + GAP) * zoom;

        // Die Kacheln haengen an der Bandmitte, nicht an der Bandkante: nur der Rahmen darf
        // beim Wachsen auf ganze Pixel springen, der Streifen selbst nicht.
        int bandHeight = Math.round(tileSize) + 2 * (PADDING + BORDER);
        float tileCenterY = bandCenterY + BountyRoll.shakeY(partial);
        int bandTop = Math.round(tileCenterY) - bandHeight / 2;

        RenderSystem.enableBlend();
        drawVignette(graphics, bandTop, bandHeight, open, speed);

        // Aufziehen und Zusammenfahren aus der Mitte, nicht ein- und ausblenden: Minecraft
        // blendet nichts weich, eine Alpha-Blende wirkt hier wie aus einem anderen Spiel.
        int visible = Math.max(2, Math.round(width * open));
        int clipLeft = (width - visible) / 2;

        graphics.enableScissor(clipLeft, bandTop, clipLeft + visible, bandTop + bandHeight);
        graphics.fill(-BLEED, bandTop, width + BLEED, bandTop + bandHeight, FRAME);
        graphics.fill(-BLEED, bandTop + BORDER, width + BLEED, bandTop + bandHeight - BORDER, TRACK);

        drawReel(graphics, partial, elapsed, speed, centerX, tileCenterY, tileSize, pitch, width);
        fadeEdges(graphics, bandTop, bandHeight, width);
        drawPointers(graphics, centerX, bandTop, bandHeight, Math.round(tileSize));
        drawFlash(graphics, elapsed, bandTop, bandHeight, width);
        graphics.disableScissor();

        drawName(graphics, minecraft, elapsed, centerX, bandTop + bandHeight);
        drawFlight(graphics, elapsed, centerX, tileCenterY, tileSize);

        RenderSystem.disableBlend();
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    /** 0.0 wenn das Band geschlossen ist, 1.0 wenn es steht. */
    private static float openFactor(float elapsed) {
        if (elapsed < BountyRollTiming.TITLE_END) {
            return 0f;
        }
        if (elapsed < BountyRollTiming.TITLE_END + BountyRollTiming.OPEN_TICKS) {
            return (elapsed - BountyRollTiming.TITLE_END) / BountyRollTiming.OPEN_TICKS;
        }
        if (elapsed <= BountyRollTiming.SNAP_END) {
            return 1f;
        }
        return Mth.clamp(1f - (elapsed - BountyRollTiming.SNAP_END) / BountyRollTiming.FLY_TICKS, 0f, 1f);
    }

    /**
     * Die durchziehenden Koepfe.
     *
     * <p>Alles wird relativ zum Zeiger gerechnet, nicht zu einem festen Nullpunkt. Nur so
     * bleibt die Kachel unter dem Zeiger stehen, waehrend die Kacheln wachsen — sonst
     * wuerde der ganze Streifen beim Groesserwerden zur Seite rutschen.
     */
    private static void drawReel(GuiGraphics graphics, float partial, float elapsed, float speed,
                                 int centerX, float tileCenterY, float tileSize, float pitch, int width) {
        float position = BountyRoll.tiles(partial);
        int middle = Math.round(position);
        int span = (int) (width / (2 * pitch)) + 2;

        float snap = snapProgress(elapsed);
        boolean flying = elapsed > BountyRollTiming.SNAP_END;

        for (int index = middle - span; index <= middle + span; index++) {
            boolean landing = index == BountyRollTiming.LANDING_INDEX;

            // Waehrend des Abgangs uebernimmt der fliegende Kopf. Ohne das stuenden zwei
            // davon auf dem Bildschirm: einer im Band, einer unterwegs in die Ecke.
            if (landing && flying) {
                continue;
            }

            float offset = index - position;

            // Nur die Landekachel pulst, und nur waehrend des Einrastens.
            float pulse = landing && snap > 0f ? 1f + SNAP_PULSE * Mth.sin(snap * Mth.PI) : 1f;

            shadeFor(graphics, offset, speed);
            drawTile(graphics, BountyRoll.reelTile(index), centerX + offset * pitch, tileCenterY,
                    tileSize * pulse, landing);
        }
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    /**
     * Der Spotlight: je langsamer der Streifen, desto dunkler die Nachbarn des Zeigers.
     *
     * <p>Am Ende steht der Gewinner allein im Licht. Das lenkt den Blick genau dorthin, wo
     * gleich etwas passiert, ohne dass irgendwo ein Pfeil blinken muesste.
     *
     * @param offset Abstand zum Zeiger, in Kacheln
     */
    private static void shadeFor(GuiGraphics graphics, float offset, float speed) {
        float focus = 1f - speed;
        float distance = Mth.clamp(Math.abs(offset) / SPOTLIGHT_SPREAD, 0f, 1f);
        float shade = 1f - focus * distance * SPOTLIGHT_DEPTH;
        graphics.setColor(shade, shade, shade, 1f);
    }

    /**
     * Zeichnet eine Kachel an einer Bruchteil-Position, wahlweise vergroessert.
     *
     * @param target ob es das ausgeloste Ziel ist — nur fuer das lohnt sich ein
     *               Skin-Nachschlag, der Rest des Streifens ist Fuellmaterial
     */
    private static void drawTile(GuiGraphics graphics, @Nullable UUID reelTile,
                                 float centerX, float centerY, float drawnSize, boolean target) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 0f);
        pose.scale(drawnSize / TILE, drawnSize / TILE, 1f);
        pose.translate(-(TILE / 2f), -(TILE / 2f), 0f);
        if (target) {
            PlayerHead.drawTarget(graphics, reelTile, 0, 0, TILE, UNKNOWN);
        } else {
            PlayerHead.draw(graphics, reelTile, 0, 0, TILE, UNKNOWN);
        }
        pose.popPose();
    }

    /**
     * Laesst die Koepfe an den Raendern ins Dunkle laufen.
     *
     * <p>Spaltenweise statt als Verlauf: {@code fillGradient} kann nur von oben nach unten.
     */
    private static void fadeEdges(GuiGraphics graphics, int bandTop, int bandHeight, int width) {
        int top = bandTop + BORDER;
        int bottom = bandTop + bandHeight - BORDER;

        for (int step = 0; step < FADE_WIDTH; step++) {
            int alpha = 0xFF - (0xFF * step / FADE_WIDTH);
            int color = (alpha << 24) | (TRACK & 0x00FFFFFF);
            graphics.fill(-BLEED + step, top, -BLEED + step + 1, bottom, color);
            graphics.fill(width + BLEED - step - 1, top, width + BLEED - step, bottom, color);
        }
    }

    /** Zwei Dreiecke, die von oben und unten auf die Mitte zeigen, plus ein Rahmen darum. */
    private static void drawPointers(GuiGraphics graphics, int centerX, int bandTop,
                                     int bandHeight, int tile) {
        int top = bandTop + BORDER;
        int bottom = bandTop + bandHeight - BORDER;

        for (int row = 0; row < POINTER_HEIGHT; row++) {
            int downHalf = POINTER_HEIGHT - 1 - row;
            graphics.fill(centerX - downHalf, top + row, centerX + downHalf + 1, top + row + 1, ACCENT);
            graphics.fill(centerX - row, bottom - POINTER_HEIGHT + row,
                    centerX + row + 1, bottom - POINTER_HEIGHT + row + 1, ACCENT);
        }

        int left = centerX - tile / 2 - 1;
        int right = centerX + tile / 2 + 1;
        int boxTop = bandTop + BORDER + PADDING - 1;
        int boxBottom = boxTop + tile + 2;
        graphics.fill(left, boxTop, left + 1, boxBottom, MARKER_BOX);
        graphics.fill(right - 1, boxTop, right, boxBottom, MARKER_BOX);
    }

    /** Kurzes Aufblitzen im Moment des Einrastens. */
    private static void drawFlash(GuiGraphics graphics, float elapsed, int bandTop,
                                  int bandHeight, int width) {
        float snap = snapProgress(elapsed);
        if (snap <= 0f) {
            return;
        }

        float strength = Math.max(0f, 1f - snap * 4f);
        if (strength <= 0f) {
            return;
        }

        int alpha = (int) (strength * 0x88);
        graphics.fill(-BLEED, bandTop + BORDER, width + BLEED, bandTop + bandHeight - BORDER,
                (alpha << 24) | 0x00FFFFFF);
    }

    /**
     * Die Vignette: das Bild dunkelt zu den Kanten hin nach und schwillt an, je naeher der
     * Streifen dem Stillstand kommt.
     *
     * <p>Nicht der abgedunkelte Vollbildschirm — die Bandhoehe selbst bleibt frei, und was
     * dort passiert, passiert vor einem sichtbaren Spiel.
     */
    private static void drawVignette(GuiGraphics graphics, int bandTop, int bandHeight,
                                     float open, float speed) {
        float strength = open * Mth.lerp(speed, 1f, 0.45f);
        if (strength <= 0.01f) {
            return;
        }

        int dark = ((int) (strength * VIGNETTE_MAX_ALPHA)) << 24;
        int clear = 0x00000000;

        int width = graphics.guiWidth();
        int reach = Math.round(graphics.guiHeight() * VIGNETTE_REACH);
        int bandBottom = bandTop + bandHeight;
        int upperEdge = Math.max(0, bandTop - reach);

        graphics.fillGradient(0, 0, width, upperEdge, dark, dark);
        graphics.fillGradient(0, upperEdge, width, bandTop, dark, clear);
        graphics.fillGradient(0, bandBottom, width, bandBottom + reach, clear, dark);
        graphics.fillGradient(0, bandBottom + reach, width, graphics.guiHeight(), dark, dark);
    }

    /** Der Name des Ausgelosten, sobald der Streifen steht. */
    private static void drawName(GuiGraphics graphics, Minecraft minecraft, float elapsed,
                                 int centerX, int bandBottom) {
        float snap = snapProgress(elapsed);
        if (snap <= 0f) {
            return;
        }

        String name = BountyRoll.getTargetName();
        Component text = name.isEmpty()
                ? Component.translatable("mchelden.bounty.none_assigned")
                : Component.literal(name);

        // Der Name kommt mit dem Gong heraus, statt einfach da zu sein.
        float grow = Mth.clamp(snap * 5f, 0f, 1f);
        float eased = grow * grow * (3f - 2f * grow);

        // Lange Namen duerfen nicht aus dem Bild wachsen.
        int textWidth = minecraft.font.width(text);
        float fitting = (graphics.guiWidth() - 2f * TEXT_MARGIN) / textWidth;
        float scale = Mth.lerp(eased, 0.5f, Math.min(NAME_SCALE, fitting));

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, bandBottom + NAME_GAP, 0f);
        pose.scale(scale, scale, 1f);
        graphics.drawString(minecraft.font, text, -textWidth / 2, 0, NAME, true);
        pose.popPose();
    }

    /**
     * Der Abgang: der Gewinnerkopf schrumpft nach oben links in seinen Kasten.
     *
     * <p>Damit ist ohne ein Wort erklaert, wo dieser Kopf ab jetzt zu finden ist. Er wird
     * ausserhalb des Bandes gezeichnet — sonst schnitte ihn dessen Rahmen ab.
     */
    private static void drawFlight(GuiGraphics graphics, float elapsed, int centerX,
                                   float tileCenterY, float tileSize) {
        if (elapsed <= BountyRollTiming.SNAP_END) {
            return;
        }

        float progress = Mth.clamp((elapsed - BountyRollTiming.SNAP_END) / BountyRollTiming.FLY_TICKS, 0f, 1f);
        float eased = progress * progress * (3f - 2f * progress);

        float targetX = BountyHud.headLeft(graphics) + BountyHud.HEAD / 2f;
        float targetY = BountyHud.headTop(graphics) + BountyHud.HEAD / 2f;

        drawTile(graphics, BountyRoll.reelTile(BountyRollTiming.LANDING_INDEX),
                Mth.lerp(eased, centerX, targetX),
                Mth.lerp(eased, tileCenterY, targetY),
                Mth.lerp(eased, tileSize, BountyHud.HEAD), true);
    }

    /**
     * Die Ansage vor dem Lauf: sie springt auf, steht kurz und ist wieder weg.
     *
     * <p>Selbst gezeichnet statt als Vanilla-Titel. Der kann nicht anders als vierfach
     * vergroessern — eine Zeile wie "Die Bounties sind vergeben" haengt damit links und
     * rechts aus dem Bild. Hier passt sie sich der Breite an.
     */
    private static void drawAnnouncement(GuiGraphics graphics, Minecraft minecraft, float elapsed,
                                         int centerX, int centerY, int width) {
        // Aufspringen und Verschwinden ueber die Groesse, nicht ueber die Deckkraft:
        // Minecraft blendet nichts weich, und eine Alpha-Blende faellt hier auf.
        float presence = elapsed < TITLE_IN
                ? elapsed / TITLE_IN
                : Mth.clamp((BountyRollTiming.TITLE_END - elapsed) / TITLE_OUT, 0f, 1f);
        if (presence <= 0f) {
            return;
        }

        float eased = presence * presence * (3f - 2f * presence);

        // Die Schluessel kommen aus den Sprachdateien, die Farben aber von hier: das Band hat
        // seine eigene Palette, und die Chat-Farben aus HeldenText passen nicht dazu.
        Component title = Component.translatable("mchelden.bounty.roll.title");
        Component subtitle = Component.translatable("mchelden.bounty.roll.subtitle");

        drawFitted(graphics, minecraft, title, centerX, centerY - 14, width, TITLE_SCALE, eased, ACCENT);
        drawFitted(graphics, minecraft, subtitle, centerX, centerY + 12, width, SUBTITLE_SCALE, eased, NAME);
    }

    /**
     * Zeichnet eine Zeile mittig, so gross wie moeglich, aber nie ueber den Bildschirmrand.
     *
     * @param presence 0.0 beim Aufspringen, 1.0 wenn sie voll dasteht
     */
    private static void drawFitted(GuiGraphics graphics, Minecraft minecraft, Component text,
                                   int centerX, int centerY, int width, float maxScale,
                                   float presence, int color) {
        int textWidth = minecraft.font.width(text);
        float fitting = (width - 2f * TEXT_MARGIN) / textWidth;
        float scale = Math.min(maxScale, fitting) * Mth.lerp(presence, 0.7f, 1f);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 0f);
        pose.scale(scale, scale, 1f);
        graphics.drawString(minecraft.font, text, -textWidth / 2, -minecraft.font.lineHeight / 2,
                color, true);
        pose.popPose();
    }

    /** 0.0 solange der Streifen laeuft, danach 0..1 ueber das Einrasten. */
    private static float snapProgress(float elapsed) {
        if (elapsed < BountyRollTiming.CREEP_END) {
            return 0f;
        }
        return Mth.clamp((elapsed - BountyRollTiming.CREEP_END) / BountyRollTiming.SNAP_TICKS, 0f, 1f);
    }
}
