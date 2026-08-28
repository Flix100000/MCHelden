package net.bananemdnsa.mchelden.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Wie schnell der Timer waechst. Frueher zaehlte jeder Treffer, damit war nach sechs
 * Schlaegen der Deckel von drei Minuten erreicht — ein normaler Kampf hat den Timer also
 * sofort ausgereizt und jeden Beteiligten minutenlang festgehalten.
 *
 * <p>Jetzt zaehlt der erste Treffer und danach jeder fuenfte. Eine Rechnung, bei der man
 * sich leicht um einen Treffer vertut und die man im Spiel nur mit der Stoppuhr nachpruefen
 * koennte. Deswegen hier.
 */
class CombatTrackerTest {

    /** Der schlimmste Fall: alle Treffer fallen sofort, dazwischen tickt nichts herunter. */
    private static int nachTreffern(int anzahl) {
        int ticks = 0;
        for (int treffer = 1; treffer <= anzahl; treffer++) {
            ticks = CombatTracker.extendedTicks(ticks, treffer);
        }
        return ticks;
    }

    private static int sekunden(int ticks) {
        return ticks / 20;
    }

    @Test
    void derErsteTrefferStartetDenTimer() {
        assertEquals(30, sekunden(nachTreffern(1)));
    }

    @Test
    void trefferZweiBisVierVerlaengernNicht() {
        assertEquals(30, sekunden(nachTreffern(2)));
        assertEquals(30, sekunden(nachTreffern(3)));
        assertEquals(30, sekunden(nachTreffern(4)));
    }

    @Test
    void derFuenfteTrefferVerlaengert() {
        assertEquals(60, sekunden(nachTreffern(5)));
    }

    @Test
    void zwischenFuenfUndZehnPassiertWiederNichts() {
        assertEquals(60, sekunden(nachTreffern(9)));
    }

    @Test
    void derZehnteTrefferVerlaengertWieder() {
        assertEquals(90, sekunden(nachTreffern(10)));
    }

    /** Erster, fuenfter, zehnter, ... — beim 25. sind die sechs Stufen voll. */
    @Test
    void derDeckelIstBeimFuenfundzwanzigstenTrefferErreicht() {
        assertEquals(180, sekunden(nachTreffern(25)));
    }

    @Test
    void ueberDemDeckelWaechstNichtsMehr() {
        assertEquals(CombatTracker.MAX_TICKS, nachTreffern(500));
    }

    /** Der Deckel darf auch dann nicht ueberschritten werden, wenn er schon fast voll ist. */
    @Test
    void einTrefferKurzUnterDemDeckelUeberzaehltNicht() {
        assertEquals(CombatTracker.MAX_TICKS,
                CombatTracker.extendedTicks(CombatTracker.MAX_TICKS - 20, 5));
    }

    @Test
    void derAllerersteTrefferIstTrefferEins() {
        assertEquals(1, CombatTracker.nextHitNumber(0, 0));
    }

    @Test
    void schlagAufSchlagWirdWeitergezaehlt() {
        assertEquals(5, CombatTracker.nextHitNumber(4, 0));
    }

    /** Ein Kampf mit Pausen zaehlt weiter, solange keine Pause die 30s reisst. */
    @Test
    void knappUnterDemFensterZaehltNochWeiter() {
        assertEquals(5, CombatTracker.nextHitNumber(4, CombatTracker.HIT_WINDOW_TICKS - 1));
    }

    @Test
    void genauNachDreissigSekundenFaengtDieZaehlungNeuAn() {
        assertEquals(1, CombatTracker.nextHitNumber(4, CombatTracker.HIT_WINDOW_TICKS));
    }

    /** Auch mitten im laufenden Timer: lange nichts getroffen, Zaehler weg. */
    @Test
    void langePauseSetztDenZaehlerZurueck() {
        assertEquals(1, CombatTracker.nextHitNumber(12, 100 * 20));
    }

    /**
     * Der Treffer nach der Pause zaehlt wieder als erster und legt darum sofort Zeit nach,
     * statt den Spieler bis zum fuenften warten zu lassen.
     */
    @Test
    void nachDerPauseGibtDerNaechsteTrefferWiederZeit() {
        int hitNumber = CombatTracker.nextHitNumber(7, CombatTracker.HIT_WINDOW_TICKS);
        assertEquals(90, CombatTracker.extendedTicks(60 * 20, hitNumber) / 20);
    }
}
