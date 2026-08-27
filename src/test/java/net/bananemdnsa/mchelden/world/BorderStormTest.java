package net.bananemdnsa.mchelden.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.border.WorldBorder;

import org.junit.jupiter.api.Test;

/**
 * Der Abstand zur naechsten Kante entscheidet, wie unruhig es wird — und wo die Funken
 * stehen. Rechnet er falsch, gewittert es mitten in der Arena oder gar nicht.
 */
class BorderStormTest {

    /** Eine Border von 2000 um 0,0: die Kanten liegen bei plusminus 1000. */
    private static WorldBorder border(double size) {
        WorldBorder border = new WorldBorder();
        border.setCenter(0.0, 0.0);
        border.setSize(size);
        return border;
    }

    @Test
    void inDerMitteIstDieKanteWeit() {
        assertEquals(1000.0, BorderStorm.distanceToEdge(border(2000.0), 0.0, 0.0), 1.0e-6);
    }

    @Test
    void direktAnDerKanteIstDerAbstandNull() {
        assertEquals(0.0, BorderStorm.distanceToEdge(border(2000.0), 1000.0, 0.0), 1.0e-6);
    }

    /** Die naechste der vier Kanten gewinnt, nicht die auf der eigenen Achse. */
    @Test
    void dieNaechsteKanteZaehlt() {
        assertEquals(50.0, BorderStorm.distanceToEdge(border(2000.0), 200.0, 950.0), 1.0e-6);
    }

    @Test
    void ausserhalbWirdDerAbstandNegativ() {
        assertTrue(BorderStorm.distanceToEdge(border(2000.0), 1100.0, 0.0) < 0.0);
    }

    /**
     * Die geschrumpfte Arena am Ende: 160 breit, Kanten bei plusminus 80. Wer dort in der
     * Mitte steht, ist achtzig Bloecke von der Kante weg — und damit doppelt so weit wie
     * die Reichweite des Gewitters.
     */
    @Test
    void inDerFertigenArenaStimmenDieKanten() {
        assertEquals(80.0, BorderStorm.distanceToEdge(border(160.0), 0.0, 0.0), 1.0e-6);
    }

    /** Eine Ecke ist von beiden Kanten gleich weit weg. */
    @Test
    void inDerEckeZaehlenBeideGleich() {
        assertEquals(100.0, BorderStorm.distanceToEdge(border(2000.0), 900.0, 900.0), 1.0e-6);
    }

    @Test
    void weitWegPassiertNichts() {
        assertEquals(0.0f, BorderStorm.heat(1000.0), 1.0e-6f);
        assertEquals(0.0f, BorderStorm.heat(41.0), 1.0e-6f);
    }

    @Test
    void anDerKanteIstEsAmStaerksten() {
        assertEquals(1.0f, BorderStorm.heat(0.0), 1.0e-6f);
    }

    /** Ausserhalb der Border wird es nicht staerker als direkt an der Kante. */
    @Test
    void ausserhalbBleibtEsBeiVoll() {
        assertEquals(1.0f, BorderStorm.heat(-20.0), 1.0e-6f);
    }

    @Test
    void dazwischenNimmtEsGleichmaessigZu() {
        assertTrue(BorderStorm.heat(10.0) > BorderStorm.heat(30.0));
        assertTrue(BorderStorm.heat(30.0) > 0.0f);
    }

    /** Einschlaege gibt es nur ganz nah, Funken schon weiter draussen. */
    @Test
    void eingeschlagenWirdErstNahAnDerKante() {
        assertTrue(BorderStorm.strikes(5.0));
        assertFalse(BorderStorm.strikes(30.0));
        assertTrue(BorderStorm.heat(30.0) > 0.0f);
    }
}
