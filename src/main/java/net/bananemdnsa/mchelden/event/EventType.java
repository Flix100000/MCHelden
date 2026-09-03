package net.bananemdnsa.mchelden.event;

import java.util.Set;

import javax.annotation.Nullable;

import net.bananemdnsa.mchelden.state.Phase;

import net.minecraft.network.chat.Component;

/**
 * Die Eventarten. Ein Event ist ein benanntes Zeitfenster, das ein Op startet und das nach
 * seiner Dauer von allein endet.
 *
 * <p><b>Was ein Eventtyp bewirkt, steht nicht hier.</b> Es wird an der Stelle gefragt, an
 * der es greift — {@code PlaytimeTracker.isLimited} fragt den {@code EventManager}, ob
 * gerade ein zeitfreies Event laeuft. Ein Lebenszyklus-Interface mit {@code onStart} und
 * {@code onEnd} waere fuer ein Event Geruest ohne Nutzer, und jeder Effekt, der beim Ende
 * zurueckgedreht werden muss, waere eine Fehlerquelle mehr.
 */
public enum EventType {
    /** Das Spielzeit-Limit ist ausgesetzt und die Uhr steht still. */
    NO_TIME_LIMIT("notimelimit", Phase.AUFBAU);

    /**
     * Die Literale des Zweiges {@code /helden event}.
     *
     * <p>Sie stehen hier und nicht nur im Command, damit ein Test sie gegen die Kennungen
     * halten kann: Brigadier prueft Literale vor Argumenten, ein Event namens {@code stop}
     * liesse sich also nie starten — lautlos.
     */
    public static final Set<String> RESERVED = Set.of("stop", "info");

    private final String id;
    private final Phase allowedPhase;

    EventType(String id, Phase allowedPhase) {
        this.id = id;
        this.allowedPhase = allowedPhase;
    }

    /** Die Kennung im Befehl. Englisch wie die Phasen-Kennungen. */
    public String getId() {
        return id;
    }

    /**
     * In welcher Phase dieser Typ startbar ist — und ausserhalb derer er endet.
     *
     * <p>Eine Angabe am Typ und keine Fallunterscheidung im Manager: ein spaeteres Event,
     * das gerade im Krieg Sinn ergibt, soll dafuer nichts anfassen muessen.
     */
    public Phase allowedPhase() {
        return allowedPhase;
    }

    public Component getDisplayName() {
        return Component.translatable("mchelden.event." + id);
    }

    /**
     * Loest eine Kennung auf, oder {@code null}.
     *
     * <p><b>Ohne Rueckfall</b>, und zwar in beide Richtungen: eine Tastatureingabe soll
     * sich beschweren statt zu raten, und eine Speicherdatei mit unbekannter Kennung soll
     * „kein Event" heissen und nicht „irgendeins".
     */
    @Nullable
    public static EventType byId(String id) {
        for (EventType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }
}
