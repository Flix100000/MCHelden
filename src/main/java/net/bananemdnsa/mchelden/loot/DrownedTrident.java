package net.bananemdnsa.mchelden.loot;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Hebt die Chance, dass ein Drowned seinen Trident fallen laesst.
 *
 * <p>In Vanilla steht der Trident in keiner Loot-Table: er faellt ueber den normalen
 * Ausruestungs-Drop mit {@code Mob.DEFAULT_EQUIPMENT_DROP_CHANCE} von 8,5 Prozent. Und nur
 * 6,25 Prozent der Drowned tragen ueberhaupt einen — zusammen etwa jeder zweihundertste.
 * Fuer ein Event, das ein paar Wochen laeuft, ist das kein seltener Fund, sondern keiner.
 *
 * <p>Angehoben wird deswegen die Drop-Chance selbst und nicht die Beute per Loot-Modifier.
 * Ein zusaetzlicher Pool waere ein zweiter, unabhaengiger Wurf — ein Drowned, der sichtbar
 * einen Trident haelt, liesse gelegentlich zwei fallen. Ausserdem droppt Vanilla die
 * Handwaffe stark abgenutzt; ein Trident aus einer Loot-Table waere fabrikneu. So bleibt es
 * derselbe Drop wie vorher, nur oefter, mit Abnutzung und Looting-Bonus von Vanilla.
 */
public final class DrownedTrident {

    /**
     * Chance, dass ein Drowned mit Trident ihn fallen laesst.
     *
     * <p>Mit den 6,25 Prozent Drowned, die einen tragen, sind das effektiv 1,7 Prozent —
     * rund das Dreifache von Vanilla.
     */
    public static final float DROP_CHANCE = 0.27F;

    private DrownedTrident() {
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        // Nur frisch gespawnte Drowned. Ein Drowned, der sich einen Trident vom Boden
        // aufgehoben hat, traegt Vanillas 2.0 — er laesst ihn immer fallen, und das soll
        // beim naechsten Chunk-Laden nicht auf unseren Wert heruntergestuft werden.
        if (event.getLevel().isClientSide()
                || event.loadedFromDisk()
                || !(event.getEntity() instanceof Drowned drowned)
                || !drowned.getMainHandItem().is(Items.TRIDENT)) {
            return;
        }

        drowned.setDropChance(EquipmentSlot.MAINHAND, DROP_CHANCE);
    }
}
