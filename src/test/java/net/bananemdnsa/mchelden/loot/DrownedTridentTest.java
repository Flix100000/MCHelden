package net.bananemdnsa.mchelden.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.entity.Mob;

import org.junit.jupiter.api.Test;

/**
 * Der Trident soll rund dreimal so oft fallen wie in Vanilla.
 *
 * <p>Die Zahl im Handler ist nicht die Drop-Rate, sondern nur ihr zweiter Faktor: sie gilt
 * fuer Drowned, die ueberhaupt einen Trident tragen, und das sind nur 6,25 Prozent. Wer die
 * beiden verwechselt, stellt versehentlich das Sechzehnfache ein. Deswegen steht hier die
 * Rechnung und nicht der Wert.
 */
class DrownedTridentTest {

    /**
     * Anteil der Drowned, die mit einem Trident spawnen.
     *
     * <p>Aus {@code Drowned#populateDefaultEquipmentSlots}: zehn Prozent bekommen ueberhaupt
     * etwas in die Hand, davon zehn von sechzehn einen Trident.
     */
    private static final double TRAEGER = 0.1 * (10.0 / 16.0);

    /** Vanilla: 6,25 Prozent Traeger, davon 8,5 Prozent Drop. */
    private static final double VANILLA = TRAEGER * Mob.DEFAULT_EQUIPMENT_DROP_CHANCE;

    @Test
    void sechsKommaZweiFuenfProzentDerDrownedTragenEinen() {
        assertEquals(0.0625, TRAEGER, 1.0e-9);
    }

    @Test
    void vanillaLaesstEinenVonZweihundertFallen() {
        assertEquals(0.0053, VANILLA, 1.0e-4);
    }

    @Test
    void wirLassenEtwaJedenSechzigstenFallen() {
        assertEquals(0.017, TRAEGER * DrownedTrident.DROP_CHANCE, 1.0e-3);
    }

    @Test
    void dasIstRundDasDreifache() {
        assertEquals(3.0, TRAEGER * DrownedTrident.DROP_CHANCE / VANILLA, 0.2);
    }

    /** Sollte Mojang den Standard je ueber unseren Wert heben, waere das hier eine Senkung. */
    @Test
    void esBleibtEineErhoehung() {
        assertTrue(DrownedTrident.DROP_CHANCE > Mob.DEFAULT_EQUIPMENT_DROP_CHANCE,
                "Drop-Chance " + DrownedTrident.DROP_CHANCE
                        + " liegt nicht ueber Vanillas " + Mob.DEFAULT_EQUIPMENT_DROP_CHANCE);
    }
}
