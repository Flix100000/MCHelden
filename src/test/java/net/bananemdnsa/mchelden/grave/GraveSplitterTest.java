package net.bananemdnsa.mchelden.grave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Der Grave-Split trifft jeden Tod im Projekt und ist teilweise zufällig. Ausprobieren im
 * Spiel wäre kein Nachweis — hier läuft er mit festem Startwert und nachrechenbar.
 */
class GraveSplitterTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static RandomSource fixedRandom() {
        return RandomSource.create(1234L);
    }

    private static int countOf(List<ItemStack> stacks, Item item) {
        return stacks.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static int itemCount(List<ItemStack> stacks, Item item) {
        return (int) stacks.stream().filter(stack -> stack.is(item)).count();
    }

    @Test
    void geraderStapelWirdGenauHalbiert() {
        GraveSplitter.Split split = GraveSplitter.split(List.of(),
                List.of(new ItemStack(Items.GOLDEN_APPLE, 64)), fixedRandom());

        assertEquals(32, countOf(split.keep(), Items.GOLDEN_APPLE));
        assertEquals(32, countOf(split.grave(), Items.GOLDEN_APPLE));
    }

    @Test
    void ungeraderStapelGibtDemGrabDasUebrigeStueck() {
        GraveSplitter.Split split = GraveSplitter.split(List.of(),
                List.of(new ItemStack(Items.GOLDEN_APPLE, 33)), fixedRandom());

        assertEquals(16, countOf(split.keep(), Items.GOLDEN_APPLE));
        assertEquals(17, countOf(split.grave(), Items.GOLDEN_APPLE));
    }

    @Test
    void einzelnesStapelbaresItemGehtVollstaendigInsGrab() {
        GraveSplitter.Split split = GraveSplitter.split(List.of(),
                List.of(new ItemStack(Items.GOLDEN_APPLE, 1)), fixedRandom());

        assertEquals(0, countOf(split.keep(), Items.GOLDEN_APPLE));
        assertEquals(1, countOf(split.grave(), Items.GOLDEN_APPLE));
    }

    @Test
    void vierRuestungsteileGehenZweiZuZwei() {
        GraveSplitter.Split split = GraveSplitter.split(
                List.of(new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
                        new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS)),
                List.of(), fixedRandom());

        assertEquals(2, split.keep().size());
        assertEquals(2, split.grave().size());
    }

    @Test
    void unteilbareItemsWerdenHaelftigVerteiltMitRestInsGrab() {
        GraveSplitter.Split split = GraveSplitter.split(List.of(),
                List.of(new ItemStack(Items.NETHERITE_SWORD), new ItemStack(Items.BOW),
                        new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.DIAMOND_AXE),
                        new ItemStack(Items.DIAMOND_SHOVEL)),
                fixedRandom());

        assertEquals(2, split.keep().size());
        assertEquals(3, split.grave().size());
    }

    @Test
    void nichtsGehtVerlorenUndNichtsEntsteht() {
        GraveSplitter.Split split = GraveSplitter.split(
                List.of(new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_BOOTS)),
                List.of(new ItemStack(Items.GOLDEN_APPLE, 17), new ItemStack(Items.ENDER_PEARL, 9),
                        new ItemStack(Items.NETHERITE_SWORD), new ItemStack(Items.BOW)),
                fixedRandom());

        assertEquals(17, countOf(split.keep(), Items.GOLDEN_APPLE)
                + countOf(split.grave(), Items.GOLDEN_APPLE));
        assertEquals(9, countOf(split.keep(), Items.ENDER_PEARL)
                + countOf(split.grave(), Items.ENDER_PEARL));
        assertEquals(1, itemCount(split.keep(), Items.NETHERITE_SWORD)
                + itemCount(split.grave(), Items.NETHERITE_SWORD));
        assertEquals(2, itemCount(split.keep(), Items.DIAMOND_HELMET)
                + itemCount(split.grave(), Items.DIAMOND_HELMET)
                + itemCount(split.keep(), Items.DIAMOND_BOOTS)
                + itemCount(split.grave(), Items.DIAMOND_BOOTS));
    }

    @Test
    void leereSlotsWerdenUebergangen() {
        GraveSplitter.Split split = GraveSplitter.split(
                List.of(ItemStack.EMPTY, ItemStack.EMPTY),
                List.of(ItemStack.EMPTY, new ItemStack(Items.GOLDEN_APPLE, 4), ItemStack.EMPTY),
                fixedRandom());

        assertEquals(1, split.keep().size());
        assertEquals(1, split.grave().size());
    }

    @Test
    void leeresInventarErgibtLeeresGrab() {
        GraveSplitter.Split split = GraveSplitter.split(List.of(), List.of(), fixedRandom());

        assertTrue(split.keep().isEmpty());
        assertTrue(split.grave().isEmpty());
    }

    @Test
    void xpGehtZurHaelfteInsGrabMitRestFuersGrab() {
        assertEquals(50, GraveSplitter.xpToGrave(100));
        assertEquals(17, GraveSplitter.xpToGrave(33));
        assertEquals(1, GraveSplitter.xpToGrave(1));
        assertEquals(0, GraveSplitter.xpToGrave(0));
    }
}
