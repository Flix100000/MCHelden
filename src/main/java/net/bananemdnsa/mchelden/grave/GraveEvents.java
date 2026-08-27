package net.bananemdnsa.mchelden.grave;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.bananemdnsa.mchelden.registry.MCHeldenBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Erzeugt das Grab beim Tod und teilt den Nachlass auf.
 *
 * <p>Das Grab entsteht bei **jedem** Tod, nicht nur bei Spielertoden. Der Herzverlust hängt
 * am Combat-Timer, das Grab an der Tatsache zu sterben — zwei verschiedene Regeln.
 */
public final class GraveEvents {
    /** Wie weit nach oben nach einem sicheren Platz gesucht wird. */
    private static final int SEARCH_UP = 24;

    private GraveEvents() {
    }

    /**
     * Erzeugt das Grab beim Tod.
     *
     * <p>Am Tod und nicht an den fallenden Sachen: die feuern erst, nachdem Minecraft das
     * Inventar bereits ausgeleert hat. Die getragene Ruestung waere dann nicht mehr als
     * solche erkennbar und wuerde als gewoehnliches Werkzeug behandelt, statt nach der
     * Zwei-zu-Zwei-Regel aufgeteilt zu werden.
     *
     * <p>Weil das Inventar hier geleert wird, faellt anschliessend nichts mehr auf den Boden.
     */
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        GraveReturn.remember(player.getUUID(),
                bury(player, level, wornArmor(player), carriedItems(player)));
    }

    /**
     * Teilt den Nachlass auf, setzt das Grab und leert den Spieler.
     *
     * <p>Gemeinsamer Kern beider Todeswege. Der Combat-Log darf das Grab nicht umgehen,
     * sonst waere Ausloggen die guenstigere Art zu sterben.
     *
     * @return was der Spieler behalten darf
     */
    public static List<ItemStack> bury(ServerPlayer player, ServerLevel level,
                                       List<ItemStack> armor, List<ItemStack> items) {
        GraveSplitter.Split split = GraveSplitter.split(armor, items, level.random);

        clearInventory(player);
        int graveXp = GraveSplitter.xpToGrave(player.totalExperience);
        player.setExperiencePoints(0);
        player.setExperienceLevels(0);
        player.totalExperience = 0;

        place(level, player, split.grave(), graveXp);
        return split.keep();
    }

    /**
     * Hauptinventar und Nebenhand, ohne die getragene Ruestung.
     *
     * <p>Die einzelnen Listen werden direkt gelesen statt ueber die Slot-Nummern: die
     * Ruestung liegt in derselben Nummerierung und wuerde sonst doppelt gezaehlt.
     */
    public static List<ItemStack> carriedItems(ServerPlayer player) {
        List<ItemStack> items = new ArrayList<>();
        copyNonEmpty(player.getInventory().items, items);
        copyNonEmpty(player.getInventory().offhand, items);
        return items;
    }

    public static List<ItemStack> wornArmor(ServerPlayer player) {
        List<ItemStack> armor = new ArrayList<>();
        copyNonEmpty(player.getInventory().armor, armor);
        return armor;
    }

    private static void copyNonEmpty(List<ItemStack> source, List<ItemStack> target) {
        for (ItemStack stack : source) {
            if (!stack.isEmpty()) {
                target.add(stack.copy());
            }
        }
    }

    /** Gibt beim Respawn zurück, was der Spieler behalten durfte. */
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GraveReturn.deliver(player);
        }
    }

    private static void clearInventory(ServerPlayer player) {
        player.getInventory().clearContent();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    private static void place(ServerLevel level, ServerPlayer player, List<ItemStack> contents, int xp) {
        BlockPos pos = findSafeSpot(level, player.blockPosition());
        if (pos == null) {
            // Kein Platz gefunden — lieber auf den Boden werfen als still verschwinden lassen.
            contents.forEach(stack -> player.drop(stack, false));
            return;
        }

        BlockState state = MCHeldenBlocks.GRAVE.get().defaultBlockState()
                .setValue(GraveBlock.FACING, Direction.fromYRot(player.getYRot()).getOpposite());
        level.setBlockAndUpdate(pos, state);

        if (level.getBlockEntity(pos) instanceof GraveBlockEntity grave) {
            grave.fill(player, contents, xp);
        }

        // Ins Verzeichnis: in einem ungeladenen Chunk ist dieser Stein sonst nicht mehr
        // auffindbar, und `reset graves` liefe daran vorbei.
        GraveRegistry.get(level.getServer()).add(pos, player.getUUID());
    }

    /**
     * Sucht einen Platz, an dem das Grab bestehen bleibt.
     *
     * <p>Wer in Lava, im Wasser oder über der Leere stirbt, soll seine Sachen trotzdem
     * wiederfinden können — ein Grab, das sofort verbrennt oder ins Nichts fällt, wäre
     * dasselbe wie kein Grab.
     */
    @Nullable
    private static BlockPos findSafeSpot(ServerLevel level, BlockPos origin) {
        BlockPos start = new BlockPos(origin.getX(),
                Math.max(level.getMinBuildHeight() + 1, origin.getY()), origin.getZ());

        for (int offset = 0; offset < SEARCH_UP; offset++) {
            BlockPos candidate = start.above(offset);
            if (candidate.getY() >= level.getMaxBuildHeight()) {
                break;
            }
            if (isSafe(level, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isSafe(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return (state.isAir() || state.canBeReplaced())
                && !state.is(Blocks.LAVA)
                && level.getFluidState(pos).isEmpty();
    }
}
