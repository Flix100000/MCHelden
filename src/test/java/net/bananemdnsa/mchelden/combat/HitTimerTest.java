package net.bananemdnsa.mchelden.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
class HitTimerTest {

    /** Der schlimmste Fall: alle Treffer fallen sofort, dazwischen tickt nichts herunter. */
    private static int nachTreffern(int anzahl) {
        int ticks = 0;
        for (int treffer = 1; treffer <= anzahl; treffer++) {
            ticks = HitTimer.extendedTicks(ticks, treffer);
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
        assertEquals(HitTimer.MAX_TICKS, nachTreffern(500));
    }

    /** Der Deckel darf auch dann nicht ueberschritten werden, wenn er schon fast voll ist. */
    @Test
    void einTrefferKurzUnterDemDeckelUeberzaehltNicht() {
        assertEquals(HitTimer.MAX_TICKS, HitTimer.extendedTicks(HitTimer.MAX_TICKS - 20, 5));
    }

    @Test
    void derAllerersteTrefferIstTrefferEins() {
        assertEquals(1, HitTimer.nextHitNumber(0, 0));
    }

    @Test
    void schlagAufSchlagWirdWeitergezaehlt() {
        assertEquals(5, HitTimer.nextHitNumber(4, 0));
    }

    /** Ein Kampf mit Pausen zaehlt weiter, solange keine Pause die 30s reisst. */
    @Test
    void knappUnterDemFensterZaehltNochWeiter() {
        assertEquals(5, HitTimer.nextHitNumber(4, HitTimer.HIT_WINDOW_TICKS - 1));
    }

    @Test
    void genauNachDreissigSekundenFaengtDieZaehlungNeuAn() {
        assertEquals(1, HitTimer.nextHitNumber(4, HitTimer.HIT_WINDOW_TICKS));
    }

    /** Auch mitten im laufenden Timer: lange nichts getroffen, Zaehler weg. */
    @Test
    void langePauseSetztDenZaehlerZurueck() {
        assertEquals(1, HitTimer.nextHitNumber(12, 100 * 20));
    }

    /**
     * Der Treffer nach der Pause zaehlt wieder als erster und legt darum sofort Zeit nach,
     * statt den Spieler bis zum fuenften warten zu lassen.
     */
    @Test
    void nachDerPauseGibtDerNaechsteTrefferWiederZeit() {
        int hitNumber = HitTimer.nextHitNumber(7, HitTimer.HIT_WINDOW_TICKS);
        assertEquals(90, HitTimer.extendedTicks(60 * 20, hitNumber) / 20);
    }

    /** Ein frischer Timer laeuft nicht — erst ein Treffer setzt ihn in Gang. */
    @Test
    void einFrischerTimerLaeuftNicht() {
        assertFalse(new HitTimer().isRunning());
    }

    @Test
    void einTrefferSetztDenTimerAufDreissigSekunden() {
        HitTimer timer = new HitTimer();
        timer.hit();
        assertTrue(timer.isRunning());
        assertEquals(30 * 20, timer.remainingTicks());
    }

    /** Der Timer laeuft genau dann ab, wenn der letzte Tick verbraucht ist. */
    @Test
    void derLetzteTickMeldetDenAblauf() {
        HitTimer timer = new HitTimer();
        timer.hit();
        for (int tick = 1; tick < 30 * 20; tick++) {
            assertFalse(timer.tick(), "Tick " + tick + " haette nicht ablaufen duerfen");
        }
        assertTrue(timer.tick());
    }

    /** Nach 30 Sekunden ohne Treffer faengt die Serie neu an, auch bei laufendem Timer. */
    @Test
    void eineLangePauseReisstDieSerieAuchImLaufendenTimer() {
        HitTimer timer = new HitTimer();
        timer.hit();
        timer.hit();
        timer.hit();
        timer.hit();
        for (int tick = 0; tick < HitTimer.HIT_WINDOW_TICKS; tick++) {
            timer.tick();
        }
        int beforeHit = timer.remainingTicks();
        timer.hit();
        assertEquals(beforeHit + HitTimer.HIT_TICKS, timer.remainingTicks());
    }

    /** Die Uebernahme kopiert den Stand vollstaendig — sonst platzt ein Duell zu billig. */
    @Test
    void dieUebernahmeKopiertRestzeitUndTrefferzaehler() {
        HitTimer source = new HitTimer();
        for (int hit = 0; hit < 4; hit++) {
            source.hit();
        }

        HitTimer copy = new HitTimer();
        copy.adopt(source);
        assertEquals(source.remainingTicks(), copy.remainingTicks());

        // Der fuenfte Treffer muss auch in der Kopie verlaengern.
        copy.hit();
        assertEquals(60 * 20, copy.remainingTicks());
    }
}
