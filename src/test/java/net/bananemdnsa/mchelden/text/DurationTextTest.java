package net.bananemdnsa.mchelden.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Die Dauer entscheidet, wie lange ein Abend dauert. Ein Fehler darin faellt erst auf,
 * wenn die Border schon laeuft — und dann laesst sie sich nur noch abbrechen.
 */
class DurationTextTest {

    @Test
    void stundenUndMinuten() {
        assertEquals(9_000_000L, DurationText.parseMillis("2h30m"));
    }

    /** Dieselbe Dauer, anders geschrieben. Beide muessen dasselbe ergeben. */
    @Test
    void nurMinutenErgibtDasselbe() {
        assertEquals(DurationText.parseMillis("2h30m"), DurationText.parseMillis("150m"));
    }

    @Test
    void nurStunden() {
        assertEquals(7_200_000L, DurationText.parseMillis("2h"));
    }

    @Test
    void nurSekunden() {
        assertEquals(90_000L, DurationText.parseMillis("90s"));
    }

    @Test
    void grossbuchstabenGehenAuch() {
        assertEquals(DurationText.parseMillis("2h30m"), DurationText.parseMillis("2H30M"));
    }

    /**
     * Eine nackte Zahl wird abgelehnt statt geraten: `3` koennte drei Stunden oder drei
     * Minuten heissen, und der Unterschied ist ein ganzer Abend.
     */
    @Test
    void nackteZahlWirdAbgelehnt() {
        assertEquals(DurationText.INVALID, DurationText.parseMillis("3"));
    }

    @Test
    void unbekannteEinheitWirdAbgelehnt() {
        assertEquals(DurationText.INVALID, DurationText.parseMillis("2d"));
    }

    @Test
    void einheitOhneZahlWirdAbgelehnt() {
        assertEquals(DurationText.INVALID, DurationText.parseMillis("h"));
    }

    @Test
    void leerWirdAbgelehnt() {
        assertEquals(DurationText.INVALID, DurationText.parseMillis(""));
    }

    @Test
    void nullDauerWirdAbgelehnt() {
        assertEquals(DurationText.INVALID, DurationText.parseMillis("0m"));
    }

    @Test
    void ueberDemDeckelWirdAbgelehnt() {
        assertEquals(DurationText.INVALID, DurationText.parseMillis("13h"));
    }

    @Test
    void uhrMitStunden() {
        assertEquals("2:30:00", DurationText.clock(9_000_000L));
    }

    @Test
    void uhrOhneStunden() {
        assertEquals("12:05", DurationText.clock(725_000L));
    }

    @Test
    void uhrWirdNichtNegativ() {
        assertEquals("0:00", DurationText.clock(-5_000L));
    }
}
