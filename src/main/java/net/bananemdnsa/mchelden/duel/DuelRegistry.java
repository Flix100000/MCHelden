package net.bananemdnsa.mchelden.duel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.bananemdnsa.mchelden.combat.HitTimer;

/**
 * Die laufenden Duelle.
 *
 * <p>Ein Duell ist immer gegenseitig: beide Teilnehmer zeigen auf dasselbe {@link Duel},
 * ein einseitiger Zustand kann gar nicht erst entstehen. Genau daran haengt der Herzschutz.
 *
 * <p><b>Ein Timer fuer beide.</b> Anders als im Combat-Timer, wo jeder gegen mehrere
 * gleichzeitig kaempfen kann, stehen sich hier genau zwei gegenueber — ein geteilter Timer
 * zaehlt damit dasselbe wie zwei getrennte, und beide sehen dieselbe Zahl im HUD.
 *
 * <p>Kennt keine Minecraft-Typen: der Ablauf laesst sich ohne laufenden Server pruefen.
 */
public final class DuelRegistry {
    private final Map<UUID, Duel> duels = new ConcurrentHashMap<>();

    /** Ein laufendes Duell zwischen zwei Spielern. */
    public static final class Duel {
        private final UUID first;
        private final UUID second;
        private final HitTimer timer = new HitTimer();

        private Duel(UUID first, UUID second) {
            this.first = first;
            this.second = second;
        }

        public UUID first() {
            return first;
        }

        public UUID second() {
            return second;
        }

        /** Der jeweils andere. */
        public UUID partnerOf(UUID uuid) {
            return first.equals(uuid) ? second : first;
        }

        /** Der geteilte Timer — Vorlage fuer die Uebernahme in den Combat-Timer. */
        public HitTimer timer() {
            return timer;
        }
    }

    /**
     * Oeffnet ein Duell und setzt den Timer auf dreissig Sekunden.
     *
     * <p>Die Annahme nimmt dabei die Rolle des ersten Treffers ein. Der Timer laeuft
     * deswegen los, bevor der erste Schlag faellt — sonst stuenden sich zwei Leute mit
     * einem leeren Balken gegenueber.
     */
    public void open(UUID first, UUID second) {
        Duel duel = new Duel(first, second);
        duel.timer.hit();
        duels.put(first, duel);
        duels.put(second, duel);
    }

    public boolean isDueling(UUID uuid) {
        return duels.containsKey(uuid);
    }

    @Nullable
    public UUID partnerOf(UUID uuid) {
        Duel duel = duels.get(uuid);
        return duel != null ? duel.partnerOf(uuid) : null;
    }

    /** Duellieren sich genau diese beiden miteinander? */
    public boolean arePartners(UUID first, UUID second) {
        Duel duel = duels.get(first);
        return duel != null && duel.partnerOf(first).equals(second);
    }

    public int remainingTicks(UUID uuid) {
        Duel duel = duels.get(uuid);
        return duel != null ? duel.timer.remainingTicks() : 0;
    }

    /** Zaehlt einen Treffer im Duell dieses Spielers. */
    public void hit(UUID uuid) {
        Duel duel = duels.get(uuid);
        if (duel != null) {
            duel.timer.hit();
        }
    }

    /**
     * Schliesst das Duell dieses Spielers, immer beidseitig.
     *
     * @return das geschlossene Duell, oder {@code null} wenn keines lief
     */
    @Nullable
    public Duel close(UUID uuid) {
        Duel duel = duels.remove(uuid);
        if (duel == null) {
            return null;
        }

        duels.remove(duel.partnerOf(uuid));
        return duel;
    }

    /**
     * Zaehlt alle Duell-Timer herunter.
     *
     * @return die abgelaufenen Duelle, bereits entfernt
     */
    public List<Duel> tick() {
        if (duels.isEmpty()) {
            return List.of();
        }

        List<Duel> expired = new ArrayList<>();
        // Jedes Duell steht unter beiden Teilnehmern. Getickt wird nur beim ersten, sonst
        // liefe die Uhr doppelt so schnell.
        for (Map.Entry<UUID, Duel> entry : duels.entrySet()) {
            Duel duel = entry.getValue();
            if (entry.getKey().equals(duel.first) && duel.timer.tick()) {
                expired.add(duel);
            }
        }

        for (Duel duel : expired) {
            duels.remove(duel.first);
            duels.remove(duel.second);
        }
        return expired;
    }
}
