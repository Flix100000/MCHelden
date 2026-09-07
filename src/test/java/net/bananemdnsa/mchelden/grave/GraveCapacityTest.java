package net.bananemdnsa.mchelden.grave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Das Grab muss den groesstmoeglichen Anteil fassen, den der Split ihm geben kann.
 *
 * <p>Mit 27 Plaetzen tat es das nicht, und der Ueberlauf wurde beim Befuellen still
 * geloescht — bei vollem Inventar bis zu zwoelf Stapel. Im Spiel sah das aus wie
 * "manchmal fehlt was", und es traf mit Vorliebe Werkzeuge und Eimer: {@link
 * GraveSplitter} haengt die unteilbaren Sachen als letzte an, sie standen also am Ende
 * der Liste und fielen als erste hinten ab.
 *
 * <p>Die Obergrenze wird deswegen hier nicht abgeschrieben, sondern aus der
 * Inventargeometrie abgeleitet. Baut Mojang dem Spieler je eine Reihe mehr ein, faellt
 * hier auf, dass das Grab nachgezogen werden muss.
 */
class GraveCapacityTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Hauptinventar und Nebenhand — was {@code carriedItems} einsammelt. */
    private static final int CARRIED_SLOTS = Inventory.INVENTORY_SIZE + 1;

    /** Die vier getragenen Ruestungsteile. */
    private static final int ARMOR_SLOTS = Inventory.ALL_ARMOR_SLOTS.length;

    private static RandomSource fixedRandom() {
        return RandomSource.create(1234L);
    }

    private static List<ItemStack> fullArmor() {
        return List.of(new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
                new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS));
    }

    /** Jeder belegte Platz ein voller Stapel: der Fall, der dem Grab am meisten abverlangt. */
    private static List<ItemStack> onlyStacks(int slots) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < slots; slot++) {
            items.add(new ItemStack(Items.COBBLESTONE, 64));
        }
        return items;
    }

    /**
     * Der teuerste Fall: lauter Stapel. Jeder gibt genau einen Eintrag ans Grab, waehrend
     * unteilbare Sachen sich zwei Plaetze teilen — Stapel sind also das Schlimmste, was
     * kommen kann, nicht ein Inventar voller Werkzeuge.
     */
    @Test
    void derGroessteAnteilSindAlleStapelPlusHalbeRuestung() {
        GraveSplitter.Split split = GraveSplitter.split(
                fullArmor(), onlyStacks(CARRIED_SLOTS), fixedRandom());

        assertEquals(CARRIED_SLOTS + ARMOR_SLOTS / 2, split.grave().size());
    }

    /** Der eigentliche Punkt: dieser Anteil passt hinein. */
    @Test
    void dasGrabFasstIhn() {
        GraveSplitter.Split split = GraveSplitter.split(
                fullArmor(), onlyStacks(CARRIED_SLOTS), fixedRandom());

        assertTrue(split.grave().size() <= GraveBlockEntity.SLOTS,
                "Grabanteil " + split.grave().size() + " passt nicht in "
                        + GraveBlockEntity.SLOTS + " Plaetze");
    }

    /**
     * Die Spielerhaelfte sprengt das Inventar ebenfalls — und das ist in Ordnung.
     *
     * <p>Sie kann groesser sein als die 36 Plaetze, die {@code Inventory.add} bedient. Dort
     * gibt es dafuer aber eine Antwort: {@link GraveReturn} wirft vor die Fuesse, was nicht
     * hineinpasst. Der Test haelt fest, dass dieser Fall wirklich eintreten kann — sonst
     * saehe die Behandlung dort nach ueberfluessiger Vorsicht aus und verschwaende beim
     * naechsten Aufraeumen.
     */
    @Test
    void derAnteilDesSpielersKannDasInventarSprengen() {
        GraveSplitter.Split split = GraveSplitter.split(
                fullArmor(), onlyStacks(CARRIED_SLOTS), fixedRandom());

        assertTrue(split.keep().size() > Inventory.INVENTORY_SIZE,
                "behaltener Anteil " + split.keep().size() + " passt doch ins Inventar");
    }

    /**
     * Warum es Werkzeuge und Eimer traf: sie stehen am Ende der Liste. Solange das so ist,
     * entscheidet die Kapazitaet oben darueber, ob sie ankommen.
     */
    @Test
    void unteilbareSachenStehenAmEndeDerListe() {
        List<ItemStack> items = new ArrayList<>(onlyStacks(CARRIED_SLOTS - 2));
        items.add(new ItemStack(Items.WATER_BUCKET));
        items.add(new ItemStack(Items.DIAMOND_PICKAXE));

        List<ItemStack> grave = GraveSplitter.split(fullArmor(), items, fixedRandom()).grave();

        ItemStack last = grave.get(grave.size() - 1);
        assertEquals(1, last.getMaxStackSize(), "am Ende steht kein unteilbarer Gegenstand: " + last);
    }

    /** Nichts darf unterwegs verschwinden — jeder Platz landet auf genau einer Seite. */
    @Test
    void keinPlatzGehtVerloren() {
        GraveSplitter.Split split = GraveSplitter.split(
                fullArmor(), onlyStacks(CARRIED_SLOTS), fixedRandom());

        assertEquals(CARRIED_SLOTS * 2 + ARMOR_SLOTS,
                split.keep().size() + split.grave().size(),
                "jeder Stapel wird geteilt, jedes Ruestungsteil geht ganz");
    }
}
