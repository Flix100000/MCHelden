package net.bananemdnsa.mchelden.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Der Balken zaehlt auf dem Client selbst herunter, und Clientticks laufen in echter Zeit,
 * waehrend die Serverticks dahinter zurueckfallen. Der Server zieht den Stand deswegen
 * einmal pro Sekunde nach — und eine solche Korrektur darf nicht wie ein Treffer aussehen.
 *
 * <p>Getestet wird nur, was ohne Toene auskommt: Aufleuchten und Ein-/Ausblenden haengen an
 * Klaengen, und die brauchen einen laufenden Client.
 */
class TimerStateTest {

    @Test
    void korrekturLaesstDenBalkenNichtAufleuchten() {
        TimerState timer = new TimerState();

        timer.accept(1200, false);

        assertEquals(1200, timer.ticks());
        assertEquals(0f, timer.flash(0f));
    }

    @Test
    void korrekturZiehtDenVorausgelaufenenStandNach() {
        TimerState timer = new TimerState();
        timer.accept(1200, false);
        for (int i = 0; i < 5; i++) {
            timer.tick();
        }

        // Der Server steht noch bei 1200: der Client war ihm fuenf Ticks vorausgelaufen.
        timer.accept(1200, false);

        assertEquals(1200, timer.ticks());
        assertEquals(0f, timer.flash(0f));
        assertTrue(timer.isRunning());
    }

    @Test
    void derCountdownTonKommtEinmalProSekunde() {
        assertTrue(TimerState.countdownSounds(40, -1));
    }

    /**
     * Eine Korrektur kann den Stand ueber eine Sekundengrenze zurueckschieben. Ohne dieses
     * Gedaechtnis kaeme derselbe Ton in den letzten Sekunden zweimal.
     */
    @Test
    void derselbeStandBekommtNurEinenTon() {
        assertFalse(TimerState.countdownSounds(40, 40));
    }

    @Test
    void vorDenLetztenSekundenTicktEsNicht() {
        assertFalse(TimerState.countdownSounds(1200, -1));
    }

    @Test
    void zwischenZweiSekundenTicktEsNicht() {
        assertFalse(TimerState.countdownSounds(39, -1));
    }

    @Test
    void amEndeTicktEsNichtMehr() {
        assertFalse(TimerState.countdownSounds(0, -1));
    }
}
