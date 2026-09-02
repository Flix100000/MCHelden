package net.bananemdnsa.mchelden.combat;

/**
 * Der Zeitanteil eines Kampf-Timers: Restzeit, Trefferzaehler und die Regel, wann ein
 * Treffer Zeit nachlegt.
 *
 * <p>Steht fuer sich, weil zwei Systeme dieselbe Rechnung brauchen — der Combat-Timer und
 * der Duell-Timer. Bei einer Einmischung wandert der Stand sogar vom einen in den anderen,
 * siehe {@link #adopt(HitTimer)}.
 */
public final class HitTimer {
    /** Was ein einzelner Treffer draufpackt. */
    public static final int HIT_TICKS = 30 * 20;
    /** Obergrenze, egal wie oft getroffen wird. */
    public static final int MAX_TICKS = 180 * 20;
    /** Nach dem ersten Treffer legt erst wieder jeder fuenfte Zeit nach. */
    public static final int HITS_PER_EXTENSION = 5;
    /** So lange darf eine Pause zwischen zwei Treffern sein, ohne die Serie zu reissen. */
    public static final int HIT_WINDOW_TICKS = 30 * 20;

    private int ticks;
    /** Treffer der laufenden Serie, siehe {@link #nextHitNumber(int, int)}. */
    private int hits;
    /** Ticks seit dem letzten Treffer. Ab {@link #HIT_WINDOW_TICKS} reisst die Serie. */
    private int idleTicks;

    /** Zaehlt einen Treffer und verlaengert, falls dieser Treffer Zeit nachlegt. */
    public void hit() {
        hits = nextHitNumber(hits, idleTicks);
        idleTicks = 0;
        ticks = extendedTicks(ticks, hits);
    }

    /**
     * Laesst einen Tick vergehen.
     *
     * @return true, wenn der Timer damit abgelaufen ist
     */
    public boolean tick() {
        idleTicks++;
        return --ticks <= 0;
    }

    public int remainingTicks() {
        return ticks;
    }

    public boolean isRunning() {
        return ticks > 0;
    }

    /**
     * Uebernimmt den Stand eines anderen Timers, Trefferzaehler eingeschlossen.
     *
     * <p>Fuer das Platzen eines Duells: der Duell-Timer laeuft danach als Combat-Timer
     * weiter. Wuerde nur die Restzeit uebernommen, finge die Trefferzaehlung wieder bei
     * eins an und der naechste Treffer legte sofort dreissig Sekunden nach.
     */
    public void adopt(HitTimer other) {
        ticks = other.ticks;
        hits = other.hits;
        idleTicks = other.idleTicks;
    }

    /**
     * Der Timerstand nach dem {@code hitNumber}-ten Treffer eines Kampfes.
     *
     * <p>Zeit gibt es beim ersten Treffer und danach bei jedem fünften. Würde jeder
     * Treffer zählen, wäre der Deckel schon nach sechs Schlägen erreicht — ein
     * gewöhnlicher Kampf hätte dann beide Seiten drei Minuten lang festgehalten.
     *
     * <p>Gezählt wird pro Spieler über alle Gegner zusammen: wer von zwei Leuten
     * gleichzeitig geschlagen wird, kommt schneller an den fünften Treffer.
     */
    static int extendedTicks(int currentTicks, int hitNumber) {
        boolean addsTime = hitNumber == 1 || hitNumber % HITS_PER_EXTENSION == 0;
        return addsTime ? Math.min(MAX_TICKS, currentTicks + HIT_TICKS) : currentTicks;
    }

    /**
     * Der wievielte Treffer einer Serie das ist.
     *
     * <p>Eine Serie hält nur, solange getroffen wird: liegen 30 Sekunden zwischen zwei
     * Treffern, fängt die Zählung wieder bei eins an. Sonst könnte man vier Treffer
     * über eine halbe Stunde verteilt sammeln und den fünften als Verlängerung kassieren.
     *
     * <p>Der Neustart kann auch mitten im laufenden Timer passieren — der reicht bis zu
     * drei Minuten, das Fenster nur dreissig Sekunden.
     *
     * @param ticksSinceLastHit Ticks seit dem letzten Treffer, 0 beim allerersten
     */
    static int nextHitNumber(int previousHits, int ticksSinceLastHit) {
        return ticksSinceLastHit >= HIT_WINDOW_TICKS ? 1 : previousHits + 1;
    }
}
