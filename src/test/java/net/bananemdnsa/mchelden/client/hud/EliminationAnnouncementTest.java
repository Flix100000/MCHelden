package net.bananemdnsa.mchelden.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Die Ansage beim Ausscheiden. Sie hing vorher am Vanilla-Titel, und der vergroessert
 * fest vierfach, ohne Umbruch und ohne Ruecksicht auf die Bildschirmbreite — "%s ist
 * ausgeschieden" ragte damit links und rechts aus dem Bild.
 *
 * <p>Die Breiten hier sind an der echten Schrift gemessen (ascii.png plus accented.png,
 * gerechnet wie {@code BitmapProvider}), nicht geschaetzt:
 *
 * <ul>
 *   <li>147 px — "Flix100000 ist ausgeschieden"</li>
 *   <li>190 px — derselbe Satz mit einem 16 Zeichen langen Namen</li>
 *   <li>197 px — die englische Fassung mit 16 Zeichen langem Namen</li>
 * </ul>
 *
 * <p>Der engste Fall ist ein 1920er Bildschirm mit GUI-Skalierung 4: 480 px Breite,
 * abzueglich der Raender bleiben 432 px.
 */
class EliminationAnnouncementTest {

    private static final int MARGIN = 24;
    /** Sub-Pixel-Rundung aus der Fliesskomma-Skalierung. Ein Hundertstelpixel ragt nirgends. */
    private static final float TOLERANZ = 0.01f;
    private static final float MAX_SCALE = 4f;

    private static int verfuegbar(int guiWidth) {
        return guiWidth - 2 * MARGIN;
    }

    /** Was am Ende wirklich auf dem Bildschirm breit ist. */
    private static float gezeichnet(int textWidth, int guiWidth) {
        return textWidth * EliminationAnnouncement.scaleFor(textWidth, verfuegbar(guiWidth), MAX_SCALE);
    }

    @Test
    void derDeutscheSatzPasstAufDenEngstenBildschirm() {
        assertTrue(gezeichnet(190, 480) <= verfuegbar(480) + TOLERANZ,
                "gezeichnet: " + gezeichnet(190, 480));
    }

    @Test
    void derEnglischeSatzPasstAufDenEngstenBildschirm() {
        assertTrue(gezeichnet(197, 480) <= verfuegbar(480) + TOLERANZ,
                "gezeichnet: " + gezeichnet(197, 480));
    }

    @Test
    void auchDerLaengsteNameSprengtNichtsMehr() {
        for (int guiWidth : new int[] {320, 480, 640, 853, 960, 1280}) {
            assertTrue(gezeichnet(197, guiWidth) <= verfuegbar(guiWidth) + TOLERANZ,
                    "guiWidth " + guiWidth + ": " + gezeichnet(197, guiWidth));
        }
    }

    /** Wo Platz ist, bleibt es beim Vanilla-Aussehen: vierfach, unveraendert. */
    @Test
    void aufBreitenBildschirmenBleibtEsBeiVierfach() {
        assertEquals(MAX_SCALE, EliminationAnnouncement.scaleFor(147, verfuegbar(1280), MAX_SCALE), 1.0e-6f);
    }

    @Test
    void groesserAlsDasMaximumWirdNieGezeichnet() {
        assertEquals(MAX_SCALE, EliminationAnnouncement.scaleFor(10, verfuegbar(1920), MAX_SCALE), 1.0e-6f);
    }

    /** Ein leerer Untertitel — es gibt keinen Killer — darf nicht durch Null teilen. */
    @Test
    void leererTextTeiltNichtDurchNull() {
        assertEquals(MAX_SCALE, EliminationAnnouncement.scaleFor(0, verfuegbar(640), MAX_SCALE), 1.0e-6f);
    }

    @Test
    void amAnfangIstDieAnsageNochUnsichtbar() {
        assertEquals(0, EliminationAnnouncement.alphaFor(EliminationAnnouncement.TOTAL_TICKS));
    }

    @Test
    void nachDemAufblendenStehtSieVoll() {
        assertEquals(255, EliminationAnnouncement.alphaFor(
                EliminationAnnouncement.TOTAL_TICKS - EliminationAnnouncement.FADE_IN_TICKS));
    }

    @Test
    void waehrendDesStehensBleibtSieVoll() {
        assertEquals(255, EliminationAnnouncement.alphaFor(50f));
    }

    @Test
    void amEndeIstSieWiederWeg() {
        assertEquals(0, EliminationAnnouncement.alphaFor(0f));
    }

    @Test
    void aufHalberAusblendungIstSieHalbDa() {
        int halb = EliminationAnnouncement.alphaFor(EliminationAnnouncement.FADE_OUT_TICKS / 2f);
        assertTrue(halb > 100 && halb < 155, "Deckkraft: " + halb);
    }

    /** Auch bei krummen Teiltick-Werten bleibt die Deckkraft im gueltigen Bereich. */
    @Test
    void dieDeckkraftVerlaesstNieDenGueltigenBereich() {
        for (float ticks = -5f; ticks <= EliminationAnnouncement.TOTAL_TICKS + 5f; ticks += 0.25f) {
            int alpha = EliminationAnnouncement.alphaFor(ticks);
            assertTrue(alpha >= 0 && alpha <= 255, "bei " + ticks + ": " + alpha);
        }
    }
}
