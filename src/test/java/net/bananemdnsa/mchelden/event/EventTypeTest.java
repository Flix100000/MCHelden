package net.bananemdnsa.mchelden.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.bananemdnsa.mchelden.state.Phase;

import org.junit.jupiter.api.Test;

class EventTypeTest {

    @Test
    void jedeKennungLaesstSichZurueckaufloesen() {
        for (EventType type : EventType.values()) {
            assertEquals(type, EventType.byId(type.getId()));
        }
    }

    /** Ein Command soll sich beschweren, nicht raten — dieselbe Regel wie bei {@code Phase.byId}. */
    @Test
    void unbekannteEingabeWirdAbgelehnt() {
        assertNull(EventType.byId("gibtsnicht"));
        assertNull(EventType.byId(""));
    }

    /**
     * Brigadier prueft Literale vor Argumenten. Hiesse ein Event {@code stop}, liesse es
     * sich nie starten — und zwar lautlos, weil der Zweig einfach den anderen Weg nimmt.
     */
    @Test
    void keineKennungKollidiertMitEinemLiteralDesBefehls() {
        for (EventType type : EventType.values()) {
            assertFalse(EventType.RESERVED.contains(type.getId()),
                    "Kennung " + type.getId() + " ist ein Literal von /helden event");
        }
    }

    @Test
    void dieKennungenSindEnglisch() {
        assertEquals("notimelimit", EventType.NO_TIME_LIMIT.getId());
    }

    /** Ausserhalb des Aufbaus gibt es kein Limit, das sich aussetzen liesse. */
    @Test
    void notimelimitGiltNurImAufbau() {
        assertEquals(Phase.AUFBAU, EventType.NO_TIME_LIMIT.allowedPhase());
    }
}
