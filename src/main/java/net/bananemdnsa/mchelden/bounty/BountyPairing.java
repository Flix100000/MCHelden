package net.bananemdnsa.mchelden.bounty;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.util.RandomSource;

/**
 * Die Zufallspaarung des Bounty-Rolls, ohne Server und ohne Spielerobjekte.
 *
 * <p>Bewusst eine reine Funktion: der Roll passiert im Projekt genau einmal, ein Fehler
 * darin waere nicht reparierbar, ohne die halbe Spannung zu verbrennen. Ausserhalb des
 * Spiels nachrechenbar zu sein ist hier mehr wert als jede Bequemlichkeit.
 */
public final class BountyPairing {

    /**
     * Ein gegenseitiges Paar. Die Reihenfolge der beiden hat keine Bedeutung — wer wen
     * jagt, ist dieselbe Beziehung in beide Richtungen.
     */
    public record Pair(UUID first, UUID second) {
    }

    private BountyPairing() {
    }

    /**
     * Mischt die Kandidaten und bildet gegenseitige Paare. Bei ungerader Zahl geht der
     * letzte leer aus — es gibt kein Dreieck und kein Ersatzziel.
     *
     * <p>Die uebergebene Liste bleibt unveraendert.
     */
    public static List<Pair> pair(List<UUID> candidates, RandomSource random) {
        List<UUID> shuffled = new ArrayList<>(candidates);
        shuffle(shuffled, random);

        List<Pair> pairs = new ArrayList<>(shuffled.size() / 2);
        for (int index = 0; index + 1 < shuffled.size(); index += 2) {
            pairs.add(new Pair(shuffled.get(index), shuffled.get(index + 1)));
        }
        return pairs;
    }

    /** Fisher-Yates. {@code Collections.shuffle} nimmt kein {@link RandomSource}. */
    private static void shuffle(List<UUID> list, RandomSource random) {
        for (int index = list.size() - 1; index > 0; index--) {
            int swap = random.nextInt(index + 1);
            UUID held = list.get(index);
            list.set(index, list.get(swap));
            list.set(swap, held);
        }
    }
}
