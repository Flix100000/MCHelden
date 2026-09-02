package net.bananemdnsa.mchelden.duel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Die Warteschlange der Duell-Anfragen. Sie ist der Grund, warum niemand zwei Duelle
 * gleichzeitig anfangen kann: wer irgendwo als Anfragender oder als Ziel steht, ist belegt.
 */
class DuelRequestsTest {
    private final DuelRequests requests = new DuelRequests();
    private final UUID anna = UUID.randomUUID();
    private final UUID bert = UUID.randomUUID();
    private final UUID clara = UUID.randomUUID();

    @Test
    void eineFrischeListeKenntNiemanden() {
        assertFalse(requests.isInvolved(anna));
        assertNull(requests.between(anna, bert));
    }

    @Test
    void nachDemOeffnenSindBeideBelegt() {
        requests.open(anna, bert);
        assertTrue(requests.isInvolved(anna));
        assertTrue(requests.isInvolved(bert));
        assertFalse(requests.isInvolved(clara));
    }

    /** Angenommen wird ueber das Paar, nicht ueber den Namen — sonst nimmt man die falsche an. */
    @Test
    void dieAnfrageWirdUeberBeideSeitenGefunden() {
        requests.open(anna, bert);
        assertEquals(bert, requests.between(anna, bert).target());
        assertNull(requests.between(bert, anna));
        assertNull(requests.between(anna, clara));
    }

    @Test
    void schliessenGibtBeideWiederFrei() {
        requests.open(anna, bert);
        requests.close(anna);
        assertFalse(requests.isInvolved(anna));
        assertFalse(requests.isInvolved(bert));
    }

    /** Der Ablauf ist auf den Tick genau: bei 60 Sekunden faellt sie, keinen Tick frueher. */
    @Test
    void dieAnfrageVerfaelltNachSechzigSekunden() {
        requests.open(anna, bert);

        for (int tick = 1; tick < DuelRequests.EXPIRY_TICKS; tick++) {
            assertTrue(requests.tick().isEmpty(), "Tick " + tick + " haette nichts fallen lassen duerfen");
        }

        List<DuelRequests.Request> expired = requests.tick();
        assertEquals(1, expired.size());
        assertEquals(anna, expired.get(0).requester());
        assertEquals(bert, expired.get(0).target());
        assertFalse(requests.isInvolved(anna));
        assertFalse(requests.isInvolved(bert));
    }

    /** Beim Logout faellt die Anfrage sofort, egal auf welcher Seite der Weggegangene stand. */
    @Test
    void derLogoutRaeumtDieAnfrageVonBeidenSeiten() {
        requests.open(anna, bert);
        DuelRequests.Request removed = requests.forget(bert);
        assertEquals(anna, removed.requester());
        assertFalse(requests.isInvolved(anna));
    }

    @Test
    void werNirgendsStehtLaesstSichAuchNichtVergessen() {
        assertNull(requests.forget(clara));
    }

    @Test
    void dieEigeneAnfrageLaesstSichZurueckziehen() {
        requests.open(anna, bert);
        DuelRequests.Request own = requests.byRequester(anna);
        assertEquals(bert, own.target());
        assertNull(requests.byRequester(bert));
    }
}
