package net.bananemdnsa.mchelden.grave;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.bananemdnsa.mchelden.registry.MCHeldenBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Inhalt eines Grabes: Items, gespeicherte XP, Besitzer und Todeszeitpunkt.
 *
 * <p>Das Grab verschwindet von selbst, sobald es leer ist. Ein vergessenes Grab im Wald ist
 * eine Markierung, ein leeres wäre nur Müll.
 */
public class GraveBlockEntity extends BaseContainerBlockEntity {
    public static final int SLOTS = 27;

    private static final String KEY_OWNER = "owner";
    private static final String KEY_OWNER_NAME = "ownerName";
    private static final String KEY_XP = "xp";
    private static final String KEY_DIED_AT = "diedAt";

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    @Nullable
    private UUID owner;
    private String ownerName = "";
    private int storedXp;
    /** Zeitpunkt des Todes als Spielzeit der Welt, für die Anzeige am Grab. */
    private long diedAt;

    /** Nur clientseitig genutzt, deswegen nicht gespeichert. */
    @Nullable
    private ItemStack headStack;

    public GraveBlockEntity(BlockPos pos, BlockState state) {
        super(MCHeldenBlockEntities.GRAVE.get(), pos, state);
    }

    /** Füllt ein frisches Grab. Wird direkt nach dem Setzen des Blocks aufgerufen. */
    public void fill(Player deceased, List<ItemStack> contents, int xp) {
        this.owner = deceased.getUUID();
        this.ownerName = deceased.getGameProfile().getName();
        this.storedXp = xp;
        this.diedAt = deceased.level().getGameTime();
        this.headStack = null;

        for (int slot = 0; slot < Math.min(contents.size(), SLOTS); slot++) {
            items.set(slot, contents.get(slot));
        }

        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    public void open(Player player) {
        player.openMenu(this);
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public int getStoredXp() {
        return storedXp;
    }

    public long getDiedAt() {
        return diedAt;
    }

    /**
     * Der Kopf des Toten als Item, fuer die Darstellung auf dem Grabstein.
     *
     * <p>Einmal gebaut und behalten: das Aufloesen des Skins laeuft ueber das Profil, und ein
     * neuer Stack pro Bild wuerde die Aufloesung immer wieder von vorn anstossen.
     */
    public ItemStack getHeadStack() {
        if (headStack == null) {
            headStack = new ItemStack(Items.PLAYER_HEAD);
            if (owner != null && !ownerName.isEmpty()) {
                headStack.set(DataComponents.PROFILE,
                        new ResolvableProfile(new com.mojang.authlib.GameProfile(owner, ownerName)));
            }
        }
        return headStack;
    }

    /**
     * Räumt das Grab ab, sobald es leer ist, und lässt dabei die XP als Orbs heraus.
     *
     * <p>Die XP hängen am Leeren und nicht am Öffnen: so gehören sie dem, der die Arbeit
     * macht, und nicht dem, der zufällig zuerst draufklickt.
     */
    public static void tick(Level level, BlockPos pos, BlockState state, GraveBlockEntity grave) {
        if (level.isClientSide() || !grave.isEmpty()) {
            return;
        }

        if (grave.storedXp > 0) {
            ExperienceOrb.award((net.minecraft.server.level.ServerLevel) level,
                    net.minecraft.world.phys.Vec3.atCenterOf(pos.above()), grave.storedXp);
            grave.storedXp = 0;
        }

        level.removeBlock(pos, false);
    }

    /**
     * Schickt dem Client nur die Anzeigedaten, nicht den Inhalt.
     *
     * <p>Wer davorsteht, soll Name und Kopf sehen — aber nicht wissen, was drin liegt, ohne
     * es zu oeffnen. Und ein Grab voller Kisteninhalte an jeden Umstehenden zu schicken waere
     * bei zwanzig Spielern auch unnoetiger Datenverkehr.
     */
    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (owner != null) {
            tag.putUUID(KEY_OWNER, owner);
        }
        tag.putString(KEY_OWNER_NAME, ownerName);
        tag.putInt(KEY_XP, storedXp);
        tag.putLong(KEY_DIED_AT, diedAt);
        return tag;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("mchelden.grave.title", ownerName);
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return ChestMenu.threeRows(containerId, inventory, this);
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);

        owner = tag.hasUUID(KEY_OWNER) ? tag.getUUID(KEY_OWNER) : null;
        ownerName = tag.getString(KEY_OWNER_NAME);
        storedXp = tag.getInt(KEY_XP);
        diedAt = tag.getLong(KEY_DIED_AT);
        headStack = null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);

        if (owner != null) {
            tag.putUUID(KEY_OWNER, owner);
        }
        tag.putString(KEY_OWNER_NAME, ownerName);
        tag.putInt(KEY_XP, storedXp);
        tag.putLong(KEY_DIED_AT, diedAt);
    }
}
