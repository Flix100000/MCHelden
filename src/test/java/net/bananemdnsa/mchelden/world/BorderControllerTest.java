package net.bananemdnsa.mchelden.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
