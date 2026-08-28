package net.bananemdnsa.mchelden.client.hud;

import net.minecraft.util.Mth;

/**
 * Zeitrechnung und Groesse der Ansage beim Ausscheiden.
 *
 * <p>Getrennt vom Zeichnen, weil beides nachrechenbar sein muss: die Groesse war der Fehler,
 * den diese Klasse behebt, und eine Blende, die um einen Tick danebenliegt, sieht man im
 * Spiel nicht, sondern nur an der Zahl.
 */
public final class EliminationAnnouncement {
    /** Dieselben Zeiten, die Vanilla einem Titel ohne eigene Angabe gibt. */
    public static final int FADE_IN_TICKS = 10;
    public static final int STAY_TICKS = 70;
    public static final int FADE_OUT_TICKS = 20;
    public static final int TOTAL_TICKS = FADE_IN_TICKS + STAY_TICKS + FADE_OUT_TICKS;

    private EliminationAnnouncement() {
    }

    /**
     * Die Deckkraft von 0 bis 255, gerechnet wie Vanilla sie fuer Titel rechnet.
     *
     * @param ticksLeft verbleibende Ticks, gern mit Teiltick fuer eine glatte Blende
     */
    public static int alphaFor(float ticksLeft) {
        float alpha = 255f;

        if (ticksLeft > FADE_OUT_TICKS + STAY_TICKS) {
            alpha = (TOTAL_TICKS - ticksLeft) * 255f / FADE_IN_TICKS;
        } else if (ticksLeft <= FADE_OUT_TICKS) {
            alpha = ticksLeft * 255f / FADE_OUT_TICKS;
        }

        return Mth.clamp((int) alpha, 0, 255);
    }

    /**
     * So gross darf die Zeile werden, ohne aus dem Bild zu ragen.
     *
     * <p>Der Kern der Sache: der Vanilla-Titel vergroessert fest vierfach, bricht nicht um
     * und misst nichts nach. "%s ist ausgeschieden" ist mit einem 16 Zeichen langen Namen
     * 190 Pixel breit, vierfach also 760 — auf einem 1920er Bildschirm mit GUI-Skalierung 4
     * stehen aber nur 480 zur Verfuegung. Hier schrumpft die Zeile stattdessen.
     *
     * @param available Breite abzueglich der Raender
     * @param maxScale Groesse, solange der Platz reicht — darueber geht es nie hinaus
     */
    public static float scaleFor(int textWidth, int available, float maxScale) {
        // Ein leerer Untertitel — es gibt keinen Killer — darf nicht durch Null teilen.
        if (textWidth <= 0) {
            return maxScale;
        }
        return Math.min(maxScale, available / (float) textWidth);
    }
}
