package net.bananemdnsa.mchelden.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Die Zylinderpruefung entscheidet, wo PvP aufhoert. Ein Fehler darin faellt im Spiel erst
 * auf, wenn jemand an der Grenze steht und sich wundert, warum sein Schlag durchgeht —
 * und dann ist die Verhandlung schon geplatzt.
 *
 * <p>Ein Zylinder und keine Kugel: eine Kugel verlaesst man, indem man hochbaut. Wer sich
 * bei 0,0 eine Plattform setzt, waere ploetzlich angreifbar, ohne dass sich etwas geaendert
 * haette. Die Hoehe zaehlt deswegen gar nicht mit.
 */
class SafeZoneTest {

    @Test
    void derMittelpunktLiegtDrin() {
        assertTrue(SafeZone.contains(0, 0));
    }

    @Test
    void knappInnerhalbDesRadiusLiegtDrin() {
        assertTrue(SafeZone.contains(SafeZone.RADIUS - 0.5, 0));
    }

    @Test
    void knappAusserhalbLiegtDraussen() {
        assertFalse(SafeZone.contains(SafeZone.RADIUS + 0.5, 0));
    }

    /** Die Zone wird von der Trennwand halbiert — beide Haelften gehoeren dazu. */
    @Test
    void beideSeitenDerTrennwandLiegenDrin() {
        assertTrue(SafeZone.contains(-40, 0));
        assertTrue(SafeZone.contains(40, 0));
    }

    /** Der springende Punkt gegenueber einer Kugel: die Hoehe spielt keine Rolle. */
    @Test
    void hoeheAendertNichts() {
        assertTrue(SafeZone.contains(40, 0));
        assertTrue(SafeZone.contains(40, 0));
    }

    @Test
    void diagonalZaehltDerEchteAbstand() {
        double diagonal = SafeZone.RADIUS * 0.75;
        assertTrue(SafeZone.contains(diagonal, 0));
        assertFalse(SafeZone.contains(diagonal, diagonal));
    }

    /** Auf der Achse und diagonal muss derselbe Radius gelten. */
    @Test
    void derRandIstRundUndNichtEckig() {
        double edge = SafeZone.RADIUS / Math.sqrt(2.0);
        assertTrue(SafeZone.contains(edge - 0.5, edge - 0.5));
        assertFalse(SafeZone.contains(edge + 0.5, edge + 0.5));
    }

    /**
     * Der Fehler, der im Spiel aufgefallen ist: die Sperre griff ueberall ausserhalb, nicht
     * nur an der Grenze. Wer im Kampf war, kam von jedem Punkt der Welt aus nicht mehr
     * Richtung 0,0 — und kein Monster konnte sich der Mitte naehern.
     */
    @Test
    void weitDraussenWirdNichtsBeschnitten() {
        assertNull(SafeZone.slide(400, 0, -1, 0));
        assertNull(SafeZone.slide(0, -900, 0, 5));
    }

    @Test
    void wegVonDerZoneWirdNichtBeschnitten() {
        assertNull(SafeZone.slide(SafeZone.RADIUS + 1, 0, 1, 0));
    }

    @Test
    void laengsDerGrenzeWirdNichtBeschnitten() {
        assertNull(SafeZone.slide(SafeZone.RADIUS + 0.5, 0, 0, 1));
    }

    @Test
    void drinnenWirdNichtsBeschnitten() {
        assertNull(SafeZone.slide(0, 0, 1, 1));
    }

    /** An der Grenze wird beschnitten — und das Ergebnis liegt hinterher noch draussen. */
    @Test
    void anDerGrenzeBleibtManDraussen() {
        double start = SafeZone.RADIUS + 0.2;
        double[] allowed = SafeZone.slide(start, 0, -2, 0);

        assertNotNull(allowed);
        assertFalse(SafeZone.contains(start + allowed[0], allowed[1]));
    }

    /**
     * Beschnitten wird nur der Anteil nach innen. Wer schraeg auf die Grenze zulaeuft, soll
     * an ihr entlanggleiten statt festzukleben.
     */
    @Test
    void schraegeBewegungGleitetEntlangDerGrenze() {
        double[] allowed = SafeZone.slide(SafeZone.RADIUS + 0.2, 0, -2, 2);

        assertNotNull(allowed);
        assertTrue(allowed[1] > 1.0, "die Bewegung laengs der Grenze bleibt erhalten");
    }

    @Test
    void negativeKoordinatenZaehlenGleich() {
        assertTrue(SafeZone.contains(-SafeZone.RADIUS + 0.5, 0));
        assertTrue(SafeZone.contains(0, -SafeZone.RADIUS + 0.5));
        assertFalse(SafeZone.contains(-SafeZone.RADIUS - 0.5, 0));
    }
}
