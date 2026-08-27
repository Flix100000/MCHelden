package net.bananemdnsa.mchelden.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.border.WorldBorder;

import org.junit.jupiter.api.Test;

/**
 * Der Balken der Bossbar ist die Arena. Steht er falsch, glauben zwanzig Leute zwei
 * Stunden lang eine falsche Zahl.
 */
class BorderControllerTest {

    @Test
    void vollAmAnfang() {
        assertEquals(1.0f, BorderController.progress(BorderController.START_SIZE), 1.0e-6f);
    }

    @Test
    void leerAmZiel() {
        assertEquals(0.0f, BorderController.progress(BorderController.FINAL_SIZE), 1.0e-6f);
    }

    @Test
    void halbInDerMitte() {
        double mitte = (BorderController.START_SIZE + BorderController.FINAL_SIZE) / 2.0;
        assertEquals(0.5f, BorderController.progress(mitte), 1.0e-6f);
    }

    /** Ein Op darf die Border unter das Ziel schrumpfen; der Balken bleibt trotzdem leer. */
    @Test
    void unterDemZielBleibtLeer() {
        assertEquals(0.0f, BorderController.progress(100.0), 1.0e-6f);
    }

    @Test
    void ueberDemStartBleibtVoll() {
        assertEquals(1.0f, BorderController.progress(30_000.0), 1.0e-6f);
    }

    /** Zweieinhalb Stunden, wenn niemand eine Dauer nennt. */
    @Test
    void dieVorgabeSindZweieinhalbStunden() {
        assertEquals(9_000_000L, BorderController.DEFAULT_DURATION_MILLIS);
    }

    /** Eine Border von 2000 um 0,0: die Kanten liegen bei plusminus 1000. */
    private static WorldBorder border(double size) {
        WorldBorder border = new WorldBorder();
        border.setCenter(0.0, 0.0);
        border.setSize(size);
        return border;
    }

    @Test
    void innerhalbIstNiemandDraussen() {
        assertFalse(BorderController.isOutside(border(2000.0), 0.0, 0.0));
        assertFalse(BorderController.isOutside(border(2000.0), 999.0, -999.0));
    }

    @Test
    void jenseitsDerKanteIstDraussen() {
        assertTrue(BorderController.isOutside(border(2000.0), 1001.0, 0.0));
        assertTrue(BorderController.isOutside(border(2000.0), 0.0, -1001.0));
    }

    /** Wer schon drin steht, wird nicht verschoben. */
    @Test
    void innenBleibtAllesStehen() {
        double[] pulled = BorderController.clampInside(border(2000.0), 300.0, -400.0);
        assertEquals(300.0, pulled[0], 1.0e-6);
        assertEquals(-400.0, pulled[1], 1.0e-6);
    }

    /** Hereingeholt wird bis kurz vor die Kante, nicht auf sie. */
    @Test
    void draussenWirdHereingeholt() {
        double[] pulled = BorderController.clampInside(border(2000.0), 5000.0, 0.0);
        assertEquals(1000.0 - BorderController.RESCUE_MARGIN, pulled[0], 1.0e-6);
        assertEquals(0.0, pulled[1], 1.0e-6);
    }

    /** Der Normalfall im Final War: Startpunkt bei 120, Arena bei plusminus 80. */
    @Test
    void inDerEndarenaWirdDerStartpunktHereingeholt() {
        double[] pulled = BorderController.clampInside(border(160.0), 120.0, 300.0);
        assertTrue(Math.abs(pulled[0]) <= 80.0, "x: " + pulled[0]);
        assertTrue(Math.abs(pulled[1]) <= 80.0, "z: " + pulled[1]);
    }

    /**
     * Eine winzige Border darf die Klammer nicht umdrehen. Ohne die Deckelung waere die
     * untere Grenze groesser als die obere, und das Ergebnis laege ausserhalb.
     */
    @Test
    void beiWinzigerBorderKipptDieKlammerNicht() {
        double[] pulled = BorderController.clampInside(border(6.0), 1000.0, 1000.0);
        assertTrue(Math.abs(pulled[0]) <= 3.0, "x: " + pulled[0]);
        assertTrue(Math.abs(pulled[1]) <= 3.0, "z: " + pulled[1]);
    }
}
