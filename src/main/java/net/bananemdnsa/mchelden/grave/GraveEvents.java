package net.bananemdnsa.mchelden.grave;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.bananemdnsa.mchelden.registry.MCHeldenBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
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

    /**
     * Wo das eben gesetzte Grab steht, und in welchem Tick.
     *
     * <p>Der Tick gehoert dazu: {@link #bury} laeuft auch beim Combat-Log, wo danach keine
     * Drops kommen. Ohne den Vergleich haenge der Eintrag dort stehen, und ein spaeterer
     * Tod ohne Grabplatz legte die Sachen anderer Mods in ein Grab von vorgestern.
     */
    private record FreshGrave(BlockPos pos, long gameTime) {
    }

    /** Rein transient: er wird im selben Tick gesetzt und wieder abgeholt. */
    private static final Map<UUID, FreshGrave> FRESH = new ConcurrentHashMap<>();

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

    /**
     * Gibt beim Respawn zurück, was der Spieler behalten durfte, und sagt ihm, wo der
     * Rest liegt.
     */
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        GraveReturn.deliver(player);

        // Dasselbe Event feuert auch bei der Rueckkehr aus dem End, und dabei ist niemand
        // gestorben. Ohne diese Pruefung erzaehlte die Nachricht dort vom letzten Grab von
        // vorgestern. Das End ist ueber History Stages ohnehin raus — die Zeile kostet
        // nichts und haelt die Regel gerade: die Nachricht gehoert zum Tod.
        if (!event.isEndConquered()) {
            GraveNotice.send(player);
        }
    }

    private static void clearInventory(ServerPlayer player) {
        player.getInventory().clearContent();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    /**
     * Nimmt auf, was andere Mods beim Tod fallen lassen wuerden.
     *
     * <p>Rucksaecke, Curios und alles Vergleichbare liegen nicht im Vanilla-Inventar und
     * kommen deswegen bei {@link #carriedItems} nicht vor — sie fielen bisher neben dem
     * Grab auf den Boden und despawnten dort, waehrend der Tote noch am Respawnen war.
     *
     * <p>Der Zugriff braucht keinen einzigen dieser Mods zu kennen. Das Todesereignis hat
     * das Vanilla-Inventar bereits geleert, und dieses Ereignis feuert danach — was hier
     * noch in der Liste steht, kann also nur von woanders kommen. Angemeldet auf der
     * niedrigsten Stufe, damit alle, die etwas hinzufuegen, vorher dran waren.
     */
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        FreshGrave fresh = FRESH.remove(player.getUUID());
        if (fresh == null || fresh.gameTime() != level.getGameTime()
                || event.getDrops().isEmpty()
                || !(level.getBlockEntity(fresh.pos()) instanceof GraveBlockEntity grave)) {
            return;
        }

        List<ItemStack> stacks = new ArrayList<>();
        for (ItemEntity dropped : event.getDrops()) {
            if (!dropped.getItem().isEmpty()) {
                stacks.add(dropped.getItem().copy());
            }
        }
        if (stacks.isEmpty()) {
            return;
        }

        // Dieselbe Regel wie fuer alles andere: geteilt wird, was jemand bei sich trug.
        GraveSplitter.Split split = GraveSplitter.split(List.of(), stacks, level.random);
        GraveReturn.remember(player.getUUID(), split.keep());
        dropAtGrave(level, fresh.pos(), grave.insert(split.grave()));

        // Uebernommen heisst uebernommen: sonst laege alles zusaetzlich am Boden.
        event.setCanceled(true);
    }

    /**
     * Wirft am Grab aus, was nicht mehr hineinpasst.
     *
     * <p>Sichtbar auf dem Boden statt still geloescht. Nach der Vergroesserung auf fuenf
     * Reihen sollte das nicht mehr vorkommen — aber ein Grab, das seinen Ueberlauf
     * verschweigt, war genau der Fehler davor.
     */
    private static void dropAtGrave(ServerLevel level, BlockPos pos, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            Block.popResource(level, pos, stack);
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
            dropAtGrave(level, pos, grave.fill(player, contents, xp));
            FRESH.put(player.getUUID(), new FreshGrave(pos, level.getGameTime()));
        }

        // Ins Verzeichnis: in einem ungeladenen Chunk ist dieser Stein sonst nicht mehr
        // auffindbar, und `reset graves` liefe daran vorbei. Der Zeitstempel ist derselbe,
        // den `fill` dem Grab selbst gegeben hat — beides derselbe Tick derselben Welt.
        GraveRegistry.get(level.getServer())
                .add(pos, player.getUUID(), level.getGameTime());
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
