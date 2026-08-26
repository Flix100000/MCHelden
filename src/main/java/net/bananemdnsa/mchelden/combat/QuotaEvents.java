package net.bananemdnsa.mchelden.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class QuotaEvents {
    private QuotaEvents() {
    }

    /**
     * Enderperlen werden an der fliegenden Perle abgefangen, nicht am Rechtsklick.
     *
     * <p>Eine Perle lässt sich auf mehrere Arten werfen — in die Luft, gegen einen Block,
     * aus beiden Händen. Am Rechtsklick müsste jeder dieser Wege einzeln erwischt werden,
     * und einer bliebe garantiert übrig. Die entstehende Perle gibt es dagegen nur einmal.
     */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()
                || !(event.getEntity() instanceof ThrownEnderpearl pearl)
                || !(pearl.getOwner() instanceof ServerPlayer player)) {
            return;
        }

        if (ItemQuota.tryUse(player, ItemQuota.Kind.PEARL)) {
            return;
        }

        event.setCanceled(true);
        refund(player, Items.ENDER_PEARL.getDefaultInstance());
    }

    /**
     * Verhindert das Platzieren, wenn das Kontingent leer ist.
     *
     * <p>Blockiert wird am Rechtsklick, nicht am Platzieren-Ereignis: dieses feuert erst,
     * wenn der Block schon gesetzt ist, und der Abbruch muss ihn nachtraeglich wieder
     * entfernen — was nicht zuverlaessig greift. Am Rechtsklick entsteht er gar nicht erst,
     * und das Item bleibt im Inventar.
     */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !event.getItemStack().is(Items.COBWEB)
                || ItemQuota.hasLeft(player, ItemQuota.Kind.COBWEB)) {
            return;
        }

        event.setCanceled(true);
        ItemQuota.refuse(player, ItemQuota.Kind.COBWEB);
    }

    /**
     * Zaehlt tatsaechlich platzierte Spinnweben.
     *
     * <p>Gezaehlt wird hier statt am Rechtsklick, weil nicht jeder Rechtsklick auch etwas
     * platziert — sonst wuerde ein Klick gegen eine Wand mitzaehlen.
     */
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getPlacedBlock().is(Blocks.COBWEB)) {
            ItemQuota.consume(player, ItemQuota.Kind.COBWEB);
        }
    }

    /**
     * Gibt die verbrauchte Perle zurück.
     *
     * <p>Nötig, weil die Perle beim Werfen schon aus dem Inventar verschwunden ist — ohne
     * Rückgabe würde die Sperre den Spieler bestehlen, statt ihn nur aufzuhalten.
     */
    private static void refund(ServerPlayer player, ItemStack stack) {
        if (player.getAbilities().instabuild) {
            return;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
