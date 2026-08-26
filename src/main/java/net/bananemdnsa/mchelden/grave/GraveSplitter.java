package net.bananemdnsa.mchelden.grave;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * Die 50/50-Aufteilung beim Tod.
 *
 * <p>Reine Rechnung ohne Welt und ohne Spieler, mit einwerfbarem Zufall. Dadurch lässt sie
 * sich mit festem Startwert prüfen — bei einer Regel, die jeden Tod im Projekt betrifft und
 * teilweise zufällig ist, wäre Ausprobieren im Spiel kein Nachweis.
 *
 * <p>Drei Töpfe, weil sie unterschiedlich funktionieren: Stapel werden geteilt, Rüstung geht
 * zwei zu zwei, und unteilbare Gegenstände werden zufällig verteilt. Bei ungerader Zahl
 * bekommt immer das Grab das übrige Stück.
 */
public final class GraveSplitter {

    /**
     * @param keep was der Spieler nach dem Respawn behält
     * @param grave was im Grab landet
     */
    public record Split(List<ItemStack> keep, List<ItemStack> grave) {
    }

    private GraveSplitter() {
    }

    /**
     * @param armor  die getragene Rüstung
     * @param items  Inventar und Nebenhand
     * @param random Zufallsquelle, für Tests mit festem Startwert
     */
    public static Split split(List<ItemStack> armor, List<ItemStack> items, RandomSource random) {
        List<ItemStack> keep = new ArrayList<>();
        List<ItemStack> grave = new ArrayList<>();

        splitByCount(nonEmpty(armor), random, keep, grave);

        List<ItemStack> single = new ArrayList<>();
        for (ItemStack stack : nonEmpty(items)) {
            if (stack.getMaxStackSize() == 1) {
                single.add(stack);
            } else {
                splitStack(stack, keep, grave);
            }
        }
        splitByCount(single, random, keep, grave);

        return new Split(keep, grave);
    }

    /**
     * Der Anteil der XP, der ins Grab wandert. Der Rest ist ersatzlos weg.
     *
     * <p>Der Spieler behaelt nichts — er respawnt grundsaetzlich auf null. Bei ungerader
     * Zahl bekommt das Grab das uebrige Stueck, wie bei den Items auch.
     */
    public static int xpToGrave(int totalXp) {
        return totalXp - totalXp / 2;
    }

    /**
     * Halbiert einen Stapel. Bei ungerader Anzahl bekommt das Grab das übrige Stück —
     * aus 33 werden 16 für den Spieler und 17 fürs Grab.
     */
    private static void splitStack(ItemStack stack, List<ItemStack> keep, List<ItemStack> grave) {
        int kept = stack.getCount() / 2;

        if (kept > 0) {
            keep.add(stack.copyWithCount(kept));
        }
        grave.add(stack.copyWithCount(stack.getCount() - kept));
    }

    /**
     * Verteilt unteilbare Gegenstände zufällig, Hälfte zu Hälfte.
     *
     * <p>Bewusst rein zufällig und nicht nach Wert sortiert: der Tod ist ein Glücksspiel.
     * Es kann passieren, dass das gute Schwert im Grab landet und die Schaufel im Inventar
     * bleibt — das ist die Regel, kein Fehler.
     */
    private static void splitByCount(List<ItemStack> stacks, RandomSource random,
                                     List<ItemStack> keep, List<ItemStack> grave) {
        List<ItemStack> shuffled = new ArrayList<>(stacks);
        shuffle(shuffled, random);

        int kept = shuffled.size() / 2;
        for (int index = 0; index < shuffled.size(); index++) {
            (index < kept ? keep : grave).add(shuffled.get(index));
        }
    }

    /** Fisher-Yates mit Minecrafts Zufallsquelle, damit Tests einen festen Startwert setzen können. */
    private static void shuffle(List<ItemStack> stacks, RandomSource random) {
        for (int index = stacks.size() - 1; index > 0; index--) {
            int swap = random.nextInt(index + 1);
            ItemStack held = stacks.get(index);
            stacks.set(index, stacks.get(swap));
            stacks.set(swap, held);
        }
    }

    private static List<ItemStack> nonEmpty(List<ItemStack> stacks) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                result.add(stack);
            }
        }
        return result;
    }
}
