package net.bananemdnsa.mchelden.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Die Zeitrechnung des Bruchs. Sie entscheidet, wann eine Scherbe losfliegt und wann sie
 * verschwunden ist — ein Fehler darin laesst entweder Truemmer stehen oder die ganze
 * Kuppel in einem einzigen Bild verschwinden.
 */
class SafeZoneShatterTest {

    @Test
    void vorDemEigenenEinsatzLiegtEineScherbeStill() {
        assertEquals(0.0f, SafeZoneShatter.progress(0, 0.0f, 8), 1.0e-6f);
    }

    @Test
    void amEndeIstSieDurch() {
        assertEquals(1.0f, SafeZoneShatter.progress(SafeZoneShatter.TOTAL_TICKS, 0.0f, 8), 1.0e-6f);
    }

    @Test
    void dazwischenLaeuftSieDurch() {
        float mitte = SafeZoneShatter.progress(SafeZoneShatter.TOTAL_TICKS / 2, 0.0f, 0);
        assertTrue(mitte > 0.0f && mitte < 1.0f, "Fortschritt in der Mitte: " + mitte);
    }

    @Test
    void spaetereScherbenHinkenHinterher() {
        int ticks = SafeZoneShatter.TOTAL_TICKS / 2;
        assertTrue(SafeZoneShatter.progress(ticks, 0.0f, 0)
                > SafeZoneShatter.progress(ticks, 0.0f, 12));
    }

    @Test
    void dieDeckkraftFaelltAufNull() {
        assertEquals(1.0f, SafeZoneShatter.alpha(0.0f), 1.0e-6f);
        assertEquals(0.0f, SafeZoneShatter.alpha(1.0f), 1.0e-6f);
    }

    /**
     * Waehrend des Fallens bleibt die Scherbe voll sichtbar. Ein Verblassen, das sofort
     * beginnt, nimmt genau den Teil weg, den man sehen will.
     */
    @Test
    void inDerErstenHaelfteBleibtSieVollSichtbar() {
        assertEquals(1.0f, SafeZoneShatter.alpha(0.25f), 1.0e-6f);
        assertEquals(1.0f, SafeZoneShatter.alpha(0.5f), 1.0e-6f);
    }

    @Test
    void danachNimmtDieDeckkraftAb() {
        assertTrue(SafeZoneShatter.alpha(0.7f) > SafeZoneShatter.alpha(0.9f));
        assertTrue(SafeZoneShatter.alpha(0.9f) > 0.0f);
    }

    /** Zehn Sekunden: kurz genug fuer einen Moment, lang genug zum Zusehen. */
    @Test
    void derBruchDauertZehnSekunden() {
        assertEquals(200, SafeZoneShatter.TOTAL_TICKS);
    }

    /** Derselbe Platz muss auf jedem Client dieselbe Scherbe ergeben. */
    @Test
    void derZufallIstWiederholbar() {
        assertEquals(SafeZoneShatter.noise(17, 5, 3), SafeZoneShatter.noise(17, 5, 3));
    }

    @Test
    void derZufallLiegtZwischenNullUndEins() {
        for (int segment = 0; segment < SafeZoneShatter.SEGMENTS; segment++) {
            for (int row = 0; row < SafeZoneShatter.ROWS; row++) {
                float value = SafeZoneShatter.noise(segment, row, 1);
                assertTrue(value >= 0.0f && value < 1.0f, "Wert " + value);
            }
        }
    }

    /** Benachbarte Plaetze duerfen nicht dieselbe Zahl bekommen, sonst kippt alles gleich. */
    @Test
    void nachbarnBekommenVerschiedeneWerte() {
        assertTrue(SafeZoneShatter.noise(4, 4, 1) != SafeZoneShatter.noise(5, 4, 1));
        assertTrue(SafeZoneShatter.noise(4, 4, 1) != SafeZoneShatter.noise(4, 5, 1));
    }

    /** Jede Scherbe muss innerhalb der Gesamtdauer fertig werden. */
    @Test
    void keineScherbeBleibtLiegen() {
        for (int segment = 0; segment < SafeZoneShatter.SEGMENTS; segment++) {
            for (int row = 0; row < SafeZoneShatter.ROWS; row++) {
                int delay = SafeZoneShatter.delay(segment, row);
                assertTrue(delay < SafeZoneShatter.MAX_DELAY, "Verzoegerung " + delay);
                assertEquals(1.0f,
                        SafeZoneShatter.progress(SafeZoneShatter.TOTAL_TICKS, 0.0f, delay),
                        1.0e-6f);
            }
        }
    }
}
