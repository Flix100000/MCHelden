package net.bananemdnsa.mchelden.combat;

import net.bananemdnsa.mchelden.registry.MCHeldenBlocks;
import net.bananemdnsa.mchelden.text.HeldenText;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Sperrt im Kampf jeden Zugriff ausser dem eigenen Inventar.
 *
 * <p>Sinn der Regel ist, dass niemand mitten im Kampf nachladen kann. Statt Kisten, Öfen,
 * Werkbänke und Ambosse einzeln aufzuzählen, wird gefragt, ob der Block überhaupt einen
 * Bildschirm öffnen würde — damit ist die Regel vollständig und kann nichts vergessen,
 * auch nichts, was Mojang später hinzufügt.
 *
 * <p>Das eigene Inventar braucht keine Ausnahme: es wird über eine Taste geöffnet und läuft
 * gar nicht erst durch diese Ereignisse.
 *
 * <p>Gräber sind ausgenommen. Der Timer läuft nach einem Kill bis zu drei Minuten weiter,
 * und wer seine eigene Beute nicht anfassen darf, während jeder Dritte sie mitnehmen kann,
 * wird um den Sieg betrogen.
 */
public final class ContainerLock {
    private ContainerLock() {
    }

    /** Kisten, Enderkisten, Shulker, Öfen, Werkbänke, Ambosse, Braustände. */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !FightState.isFighting(player.getUUID())) {
            return;
        }

        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (state.getMenuProvider(event.getLevel(), event.getPos()) == null
                || state.is(MCHeldenBlocks.GRAVE.get())) {
            return;
        }

        event.setCanceled(true);
        deny(player);
    }

    /** Esel und Lamas mit Kiste, Kistenboote, Kistenloren — mobiler Lagerraum. */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !FightState.isFighting(player.getUUID())) {
            return;
        }

        Entity target = event.getTarget();
        if (!(target instanceof HasCustomInventoryScreen) && !(target instanceof ContainerEntity)) {
            return;
        }

        event.setCanceled(true);
        deny(player);
    }

    /**
     * Kurzer Verschluss-Sound und ein knapper Hinweis.
     *
     * <p>Ohne Rückmeldung fühlt sich eine gesperrte Kiste wie ein Bug an — man klickt, nichts
     * passiert, man klickt nochmal. Die Restzeit steht permanent im HUD und muss hier nicht
     * wiederholt werden.
     */
    private static void deny(ServerPlayer player) {
        player.playNotifySound(SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.7f, 1.4f);
        player.displayClientMessage(HeldenText.containerLocked(), true);
    }
}
