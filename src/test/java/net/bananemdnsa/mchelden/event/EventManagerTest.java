package net.bananemdnsa.mchelden.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Die Rechnung hinter Bossbar und {@code /helden event info}. Reine Zahlen, damit sie ohne
 * Spielstart pruefbar ist.
 */
class EventManagerTest {

    @Test
    void restzeitZaehltHerunter() {
        assertEquals(60_000L, EventManager.remainingMillis(100_000L, 40_000L));
    }

    /** Sonst zeigte ein abgelaufenes Event eine negative Uhr, statt vorbei zu sein. */
    @Test
    void restzeitWirdNieNegativ() {
        assertEquals(0L, EventManager.remainingMillis(100_000L, 100_000L));
        assertEquals(0L, EventManager.remainingMillis(100_000L, 150_000L));
    }

    @Test
    void derBalkenStehtAmAnfangVollUndAmEndeLeer() {
        assertEquals(1.0f, EventManager.progress(0L, 60_000L, 0L), 1.0e-6f);
        assertEquals(0.5f, EventManager.progress(0L, 60_000L, 30_000L), 1.0e-6f);
        assertEquals(0.0f, EventManager.progress(0L, 60_000L, 60_000L), 1.0e-6f);
    }

    /** Ein Serverneustart kann die Uhr weit ueber das Ende hinaus tragen. */
    @Test
    void derBalkenLaeuftNieAusDemRahmen() {
        assertEquals(0.0f, EventManager.progress(0L, 60_000L, 90_000L), 1.0e-6f);
        assertEquals(1.0f, EventManager.progress(0L, 60_000L, -5_000L), 1.0e-6f);
    }

    /** Kein Nulldurchlauf: ein Fenster ohne Dauer ist vorbei, nicht unendlich. */
    @Test
    void einFensterOhneDauerIstVorbei() {
        assertEquals(0.0f, EventManager.progress(1_000L, 1_000L, 1_000L), 1.0e-6f);
    }
}
