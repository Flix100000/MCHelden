package net.bananemdnsa.mchelden.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Die Beschneidung entscheidet, ob die Trennwand eine Wand ist. Ein Fehler darin faellt im
 * Spiel entweder gar nicht auf — man laeuft hindurch — oder als unsichtbare Sperre mitten
 * auf freiem Feld.
 *
 * <p><b>Die Arenamitte steht mit in der Rechnung</b>, anders als bei der {@link SafeZone},
 * deren reine Funktionen sie nicht kennen. Genau dieser Unterschied ist der Grund: die
 * Wand hat die Verschiebung einmal nicht mitbekommen, weil sie beim Aufrufer lag und dort
 * uebersehen wurde. Steht sie im Parameter, kann sie hier gepruefte Zahlen haben.
 */
class DividerWallTest {

    @Test
    void werAufDieLinieZulaeuftWirdDavorGestoppt() {
        Double allowed = DividerWall.slide(5.0, 0.0, -10.0);

        assertNotNull(allowed);
        assertEquals(DividerWall.MARGIN - 5.0, allowed, 1.0e-9);
    }

    @Test
    void werSichVonDerLinieWegbewegtDarfDas() {
        assertNull(DividerWall.slide(5.0, 0.0, 10.0));
    }

    @Test
    void wessenSchrittVorDerLinieEndetDarfIhnGehen() {
        assertNull(DividerWall.slide(5.0, 0.0, -2.0));
    }

    /** Sonst kaeme nie wieder heraus, wen ein Teleport in der Linie abgesetzt hat. */
    @Test
    void werSchonInDerLinieStehtKommtHeraus() {
        assertNull(DividerWall.slide(0.0, 0.0, -10.0));
        assertNull(DividerWall.slide(0.0, 0.0, 10.0));
    }

    @Test
    void vonDerAnderenSeiteGiltDasselbe() {
        Double allowed = DividerWall.slide(-5.0, 0.0, 10.0);

        assertNotNull(allowed);
        assertEquals(5.0 - DividerWall.MARGIN, allowed, 1.0e-9);
    }

    /**
     * Der eigentliche Punkt: {@code /helden center} schiebt die Wand mit, und die
     * Kollision muss dorthin mitwandern, wo der Renderer sie zeichnet.
     */
    @Test
    void dieLinieWandertMitDerArenamitte() {
        Double allowed = DividerWall.slide(1005.0, 1000.0, -10.0);

        assertNotNull(allowed);
        assertEquals(DividerWall.MARGIN - 5.0, allowed, 1.0e-9);
    }

    /** Und am alten Ursprung steht dann nichts mehr. */
    @Test
    void amAltenUrsprungStehtNachDemVerschiebenKeineWandMehr() {
        assertNull(DividerWall.slide(5.0, 1000.0, -10.0));
    }
}
