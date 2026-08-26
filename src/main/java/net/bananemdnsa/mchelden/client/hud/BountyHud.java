package net.bananemdnsa.mchelden.client.hud;

import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;

import net.bananemdnsa.mchelden.MCHelden;
import net.bananemdnsa.mchelden.client.BountyRoll;
import net.bananemdnsa.mchelden.client.ClientState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Der Bounty-Kasten oben links: das eigene Ziel, oder ein Fragezeichen, solange keins
 * vergeben ist.
 *
 * <p>Der Kasten steht ab Tag 1 an derselben Stelle. Beim Roll wechselt nur sein Inhalt,
 * nichts springt — deswegen sieht das Fragezeichen davor genauso aus wie der Kopf danach.
 *
 * <p>Jede Aenderung bekommt eine kurze Bewegung: das Fragezeichen atmet, der ankommende
 * Kopf laesst den Rahmen aufleuchten, der Balken ueber einem Ausgeschiedenen faehrt ein,
 * und beim Aufloesen faehrt der Kasten zusammen statt einfach zu verschwinden. Ein Kasten,
 * der zwischen zwei Frames stillschweigend anders aussieht, wird uebersehen.
 */
public final class BountyHud {
    public static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "bounty");

    public static final int HEAD = 16;
    private static final int BORDER = 1;
    private static final int PADDING = 2;
    private static final int OUTER = HEAD + 2 * (BORDER + PADDING);
    private static final int NAME_GAP = 4;

    private static final int FRAME = 0xFF000000;
    private static final int TRACK = 0xFF0B1622;
    private static final int NAME = 0xFFDDE7F0;
    private static final int NAME_GONE = 0xFF7A7A7A;
    private static final int UNKNOWN = 0xFF8B99A6;
    /** Herzblau, dieselbe Farbe wie der Zeiger des Gluecksrads. */
    private static final int ACCENT = 0xFF5AA9F0;

    /** Wie stark der Kopf eines Ausgeschiedenen abgedunkelt wird. */
    private static final float GONE_SHADE = 0.42f;
    private static final int STRIKE = 0xE0B03A3A;
    private static final int STRIKE_HEIGHT = 2;

    /** Wie lange ein Atemzug des Fragezeichens dauert. */
    private static final float BREATH_TICKS = 70f;
    private static final float BREATH_MIN = 0.55f;
    private static final float BREATH_MAX = 1f;

    private BountyHud() {
    }

    /** Linke Kante des Kopfes im Kasten. Das Gluecksrad fliegt genau hierher. */
    public static int headLeft(GuiGraphics graphics) {
        return HudLayout.bountyLeft(graphics) + BORDER + PADDING;
    }

    public static int headTop(GuiGraphics graphics) {
        return HudLayout.bountyTop(graphics) + BORDER + PADDING;
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.player.isSpectator()) {
            return;
        }

        // Solange der Streifen laeuft, bleibt hier das Fragezeichen stehen: das Ergebnis
        // gehoert ins Band, nicht vorab in die Ecke.
        boolean rolling = BountyRoll.isRunning();
        UUID target = rolling ? null : ClientState.getBountyTargetId();
        float partial = delta.getGameTimeDeltaPartialTick(false);

        boolean closing = ClientState.isBountyClosing();
        if (target == null && !rolling && !closing && ClientState.isBountyResolved()) {
            return;
        }

        int left = HudLayout.bountyLeft(graphics);
        int top = HudLayout.bountyTop(graphics);

        RenderSystem.enableBlend();

        // Zufahren aus der Mitte, wie das Band des Gluecksrads. Verschwinden ohne Bewegung
        // wuerde aussehen, als waere die Anzeige abgestuerzt.
        float close = closing ? ClientState.bountyClose(partial) : 1f;
        int visible = Math.max(1, Math.round(OUTER * close));
        int clipLeft = left + (OUTER - visible) / 2;
        graphics.enableScissor(clipLeft, top, clipLeft + visible, top + OUTER);

        drawFrame(graphics, left, top, partial);

        boolean gone = target != null && ClientState.isBountyTargetEliminated();
        drawHead(graphics, target, headLeft(graphics), headTop(graphics), gone, partial);
        graphics.disableScissor();

        if (target != null && !closing) {
            graphics.drawString(minecraft.font, ClientState.getBountyTargetName(),
                    left + OUTER + NAME_GAP, top + (OUTER - minecraft.font.lineHeight) / 2 + 1,
                    gone ? NAME_GONE : NAME, true);
        }

        RenderSystem.disableBlend();
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    /** Der Rahmen leuchtet kurz blau auf, wenn ein Ziel im Kasten ankommt. */
    private static void drawFrame(GuiGraphics graphics, int left, int top, float partial) {
        float appear = ClientState.bountyAppear(partial);
        int frame = appear > 0f ? blend(FRAME, ACCENT, appear) : FRAME;

        graphics.fill(left, top, left + OUTER, top + OUTER, frame);
        graphics.fill(left + BORDER, top + BORDER, left + OUTER - BORDER, top + OUTER - BORDER, TRACK);
    }

    /**
     * Der Kopf, oder das Fragezeichen.
     *
     * <p>Ausgeschieden heisst: der Kopf bleibt, wird aber grau, und ein Balken faehrt von
     * links darueber. Das Fragezeichen atmet stattdessen langsam — es soll ueber Tage
     * hinweg als Versprechen lesbar bleiben, ohne je aufdringlich zu werden.
     */
    private static void drawHead(GuiGraphics graphics, @Nullable UUID target, int x, int y,
                                 boolean gone, float partial) {
        if (gone) {
            graphics.setColor(GONE_SHADE, GONE_SHADE, GONE_SHADE, 1f);
        } else if (target == null) {
            float breath = breath(partial);
            graphics.setColor(breath, breath, breath, 1f);
        }

        PlayerHead.drawTarget(graphics, target, x, y, HEAD, UNKNOWN);
        graphics.setColor(1f, 1f, 1f, 1f);

        if (!gone) {
            return;
        }

        int width = Math.round(HEAD * ClientState.bountyGone(partial));
        int bar = y + (HEAD - STRIKE_HEIGHT) / 2;
        graphics.fill(x, bar, x + width, bar + STRIKE_HEIGHT, STRIKE);
    }

    /** Ein langsamer Atemzug, damit das Fragezeichen nicht wie ein totes Bild wirkt. */
    private static float breath(float partial) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return BREATH_MAX;
        }

        float clock = (minecraft.level.getGameTime() + partial) / BREATH_TICKS * Mth.TWO_PI;
        return Mth.lerp((Mth.sin(clock) + 1f) / 2f, BREATH_MIN, BREATH_MAX);
    }

    /** Mischt zwei undurchsichtige Farben. */
    private static int blend(int from, int to, float amount) {
        int red = Math.round(Mth.lerp(amount, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
        int green = Math.round(Mth.lerp(amount, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
        int blue = Math.round(Mth.lerp(amount, from & 0xFF, to & 0xFF));
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }
}
