package net.bananemdnsa.mchelden.bounty;

/**
 * Die Zuege des Bounty-Gluecksrads und ihre Laengen.
 *
 * <p>Steht bewusst hier und nicht beim Client: der Server braucht dieselben Zahlen, weil er
 * die persoenliche Chat-Zeile erst schickt, wenn der Kopf angekommen ist. Zwei getrennte
 * Kopien wuerden irgendwann auseinanderlaufen, und man saehe es nur daran, dass das
 * Ergebnis im Chat steht, bevor der Streifen es preisgibt.
 *
 * <p>Elf Sekunden fuer eine einzige Auslosung sind viel — der Roll passiert im ganzen
 * Projekt aber genau einmal, und alle zwanzig erleben ihn gleichzeitig.
 */
public final class BountyRollTiming {
    /**
     * Ansage: der Titel steht allein auf dem Bild, bevor das Band aufgeht.
     *
     * <p>Nacheinander statt gleichzeitig. Titel und laufender Streifen zur selben Zeit
     * heben sich gegenseitig auf — man liest den einen nicht und sieht den anderen nicht.
     */
    public static final int TITLE_TICKS = 65;
    /** Anlauf: das Band faehrt auf, der Streifen zieht an. */
    public static final int OPEN_TICKS = 20;
    /** Vollgas: die Koepfe fliegen vorbei. */
    public static final int SPIN_TICKS = 55;
    /** Ausrollen: das lange Langsamerwerden, unterlegt vom Herzschlag. */
    public static final int SLOW_TICKS = 75;
    /** Fast-Stopp: es sieht aus, als waere er stehengeblieben. */
    public static final int HOLD_TICKS = 12;
    /** Und kriecht dann doch noch eine Kachel weiter. */
    public static final int CREEP_TICKS = 28;
    /** Einrasten: Ruck, Aufblitzen, Gong, Name. */
    public static final int SNAP_TICKS = 18;
    /** Abgang: der Kopf schrumpft in seinen Kasten oben links. */
    public static final int FLY_TICKS = 12;

    /** Ende der Ansage, Beginn des Bandes. */
    public static final int TITLE_END = TITLE_TICKS;
    /** Reine Laufzeit des Streifens, ohne die Ansage davor. */
    public static final int RUN_LENGTH = OPEN_TICKS + SPIN_TICKS + SLOW_TICKS;
    /** Ende des Laufs auf der vorletzten Kachel — ab hier taeuscht er den Stillstand vor. */
    public static final int RUN_END = TITLE_END + RUN_LENGTH;
    public static final int HOLD_END = RUN_END + HOLD_TICKS;
    public static final int CREEP_END = HOLD_END + CREEP_TICKS;
    public static final int SNAP_END = CREEP_END + SNAP_TICKS;
    public static final int TOTAL_TICKS = SNAP_END + FLY_TICKS;

    /** Auf welcher Kachel der Streifen haelt. Zugleich die Zahl der vorbeiziehenden Koepfe. */
    public static final int LANDING_INDEX = 64;

    private BountyRollTiming() {
    }
}
