package net.bananemdnsa.mchelden.client;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * Ein Timer-Balken auf dem Client: Restzeit, Aufleuchten, Ein- und Ausblenden.
 *
 * <p>Zweimal gebraucht — Combat-Timer und Duell-Timer laufen nach denselben Regeln und
 * unterscheiden sich nur in der Farbe. Der Client zaehlt selbst herunter, damit der Balken
 * fluessig laeuft, ohne zwanzig Pakete pro Sekunde.
 *
 * <p>Selbst zaehlen heisst aber in echter Zeit zaehlen, waehrend die Serverticks darunter
 * unter Last zurueckfallen. Der Balken lief dem Server damit voraus und war leer, bevor der
 * Kampf vorbei war — Kisten und Safezone blieben noch Sekunden laenger gesperrt. Der Server
 * zieht den Stand deswegen einmal pro Sekunde nach; {@link #accept(int, boolean)} trennt
 * eine solche Korrektur von einem Treffer.
 */
public final class TimerState {
    /** Dauer des Aufleuchtens nach einem Treffer. */
    public static final int FLASH_TICKS = 7;
    /** Wie lange der Balken beim Beginn einschwebt. */
    public static final int ENTER_TICKS = 6;
    /** Wie lange er nach dem Ende noch nachleuchtet und zusammenfaehrt. */
    public static final int EXIT_TICKS = 12;
    /** Ab wann der Countdown tickt und der Balken pulsiert. */
    public static final int WARNING_TICKS = 3 * 20;

    private int ticks;
    private int flashTicks;
    private int enterTicks;
    private int exitTicks;
    /**
     * Der Stand, der den letzten Countdown-Ton bekommen hat.
     *
     * <p>Eine Korrektur schiebt den Stand nach oben, notfalls ueber eine Sekundengrenze
     * zurueck. Ohne dieses Gedaechtnis kaeme derselbe Ton in den letzten Sekunden zweimal.
     */
    private int lastCountdownTick = -1;

    /**
     * Uebernimmt den Stand vom Server. 0 bedeutet: vorbei.
     *
     * <p>Ob ein Treffer dahintersteht, sagt der Server statt es aus einem steigenden Wert
     * zu schliessen: seine Korrekturen steigen ebenfalls, und ein aufleuchtender Balken
     * ohne Treffer waere eine Falschmeldung. Dasselbe galt bisher schon fuer den Stand, der
     * beim Verbrauch einer Perle mitkommt.
     *
     * <p>Faellt der Wert auf null, ist der Timer geraeumt worden und man darf wieder an
     * Kisten und in die Safezone. Genau das braucht einen Ton, weil man es nach drei Minuten
     * sonst nicht mitbekommt.
     *
     * @param hit true, wenn dieser Stand von einem Treffer kommt oder einen Timer eroeffnet
     */
    public void accept(int remainingTicks, boolean hit) {
        if (hit) {
            if (ticks == 0) {
                begin();
            }
            flashTicks = FLASH_TICKS;
            // Der Timer faengt von vorn an: der Ton der letzten Sekunden auch.
            lastCountdownTick = -1;
        } else if (remainingTicks == 0 && ticks > 0) {
            // Erzwungenes Raeumen, etwa per Command oder beim Tod.
            end();
        } else if (remainingTicks > 0 && ticks == 0) {
            // Der Client war vorausgelaufen und hat schon ausgeblendet, laut Server laeuft
            // der Kampf aber noch. Still zurueckholen: das Ausblenden abbrechen, ohne einen
            // zweiten Einstiegston zu spielen.
            exitTicks = 0;
        }
        ticks = remainingTicks;
    }

    /**
     * Soll dieser Stand den Countdown-Ton bekommen?
     *
     * <p>Ein Ton pro Sekunde in den letzten Sekunden, und jeder Stand nur einmal.
     *
     * @param lastSounded der Stand des letzten Tons, oder -1
     */
    static boolean countdownSounds(int ticks, int lastSounded) {
        return ticks > 0 && ticks <= WARNING_TICKS && ticks % 20 == 0 && ticks != lastSounded;
    }

    public void tick() {
        if (ticks > 0) {
            ticks--;

            // Der Client zaehlt selbst herunter und erreicht die Null oft einen Tick vor dem
            // Paket vom Server. Das Ende haengt deswegen hier und nicht am Paket — sonst
            // wird die Ausblendung genau beim regulaeren Ablaufen verschluckt.
            if (ticks == 0) {
                end();
            } else if (countdownSounds(ticks, lastCountdownTick)) {
                // Ticken in den letzten Sekunden. Ein Ton erreicht einen auch dann, wenn man
                // gerade woanders hinschaut — anders als jedes Blinken.
                lastCountdownTick = ticks;
                float step = (WARNING_TICKS - ticks) / (float) WARNING_TICKS;
                ClientState.play(SoundEvents.NOTE_BLOCK_PLING.value(), 0.7f, 1.2f + step * 0.5f);
            }
        }
        if (flashTicks > 0) {
            flashTicks--;
        }
        if (enterTicks > 0) {
            enterTicks--;
        }
        if (exitTicks > 0) {
            exitTicks--;
        }
    }

    /** Tiefer Schlag, kurz darauf ein zweiter Ton — der Eintritt bekommt einen Moment. */
    private void begin() {
        enterTicks = ENTER_TICKS;
        exitTicks = 0;
        ClientState.play(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.9f, 0.6f);
        ClientState.playDelayed(SoundEvents.NOTE_BLOCK_BIT.value(), 0.5f, 0.8f, 3);
    }

    /** Zwei aufsteigende Toene: die Anspannung loest sich, man darf wieder an die Kisten. */
    private void end() {
        exitTicks = EXIT_TICKS;
        ClientState.play(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.7f, 1.3f);
        ClientState.playDelayed(SoundEvents.NOTE_BLOCK_PLING.value(), 0.6f, 1.9f, 4);
    }

    public void reset() {
        ticks = 0;
        flashTicks = 0;
        enterTicks = 0;
        exitTicks = 0;
        lastCountdownTick = -1;
    }

    public int ticks() {
        return ticks;
    }

    public boolean isRunning() {
        return ticks > 0;
    }

    /** Balken sichtbar? Nach dem Ende noch waehrend des Ausblendens. */
    public boolean isVisible() {
        return ticks > 0 || exitTicks > 0;
    }

    /** 0.0 beim Einschweben, 1.0 wenn der Balken steht. */
    public float enter(float partialTick) {
        return 1f - Mth.clamp(Math.max(0f, enterTicks - partialTick) / ENTER_TICKS, 0f, 1f);
    }

    /** 1.0 direkt nach dem Ende, 0.0 wenn der Balken weg ist. */
    public float exit(float partialTick) {
        return Mth.clamp(Math.max(0f, exitTicks - partialTick) / EXIT_TICKS, 0f, 1f);
    }

    /** 1.0 direkt nach einem Treffer, 0.0 wenn das Aufleuchten vorbei ist. */
    public float flash(float partialTick) {
        return Mth.clamp(Math.max(0f, flashTicks - partialTick) / FLASH_TICKS, 0f, 1f);
    }
}
