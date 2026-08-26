package net.bananemdnsa.mchelden.grave;

import net.bananemdnsa.mchelden.registry.MCHeldenMenus;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Die Oberfläche eines Grabes.
 *
 * <p>Trägt neben den Plätzen auch Name, gespeicherte XP und Todeszeitpunkt zum Client —
 * die stehen in der Kopfzeile und müssen deswegen beim Öffnen mitgeschickt werden.
 */
public class GraveMenu extends AbstractContainerMenu {
    private static final int SLOTS_PER_ROW = 9;
    private static final int GRAVE_ROWS = 3;

    /** Erste Grabreihe, passend zur Kopfzeile in der Textur. */
    private static final int GRAVE_TOP = 43;
    private static final int INVENTORY_TOP = 109;
    private static final int HOTBAR_TOP = 167;
    private static final int LEFT = 8;

    private final Container container;
    private final String ownerName;
    private final int storedXp;
    private final long diedAt;

    /** Serverseitig: arbeitet direkt auf dem Grab. */
    public GraveMenu(int containerId, Inventory playerInventory, Container container,
                     String ownerName, int storedXp, long diedAt) {
        super(MCHeldenMenus.GRAVE.get(), containerId);
        this.container = container;
        this.ownerName = ownerName;
        this.storedXp = storedXp;
        this.diedAt = diedAt;

        container.startOpen(playerInventory.player);
        addGraveSlots();
        addPlayerSlots(playerInventory);
    }

    /** Clientseitig: die Anzeigedaten kommen beim Öffnen mit, der Inhalt über die üblichen Pakete. */
    public GraveMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, new SimpleContainer(GraveBlockEntity.SLOTS),
                buffer.readUtf(), buffer.readVarInt(), buffer.readLong());
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

    private void addGraveSlots() {
        for (int row = 0; row < GRAVE_ROWS; row++) {
            for (int column = 0; column < SLOTS_PER_ROW; column++) {
                addSlot(new Slot(container, column + row * SLOTS_PER_ROW,
                        LEFT + column * 18, GRAVE_TOP + row * 18));
            }
        }
    }

    private void addPlayerSlots(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < SLOTS_PER_ROW; column++) {
                addSlot(new Slot(inventory, column + row * SLOTS_PER_ROW + 9,
                        LEFT + column * 18, INVENTORY_TOP + row * 18));
            }
        }
        for (int column = 0; column < SLOTS_PER_ROW; column++) {
            addSlot(new Slot(inventory, column, LEFT + column * 18, HOTBAR_TOP));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int graveSlots = GRAVE_ROWS * SLOTS_PER_ROW;

        boolean moved = index < graveSlots
                ? moveItemStackTo(stack, graveSlots, slots.size(), true)
                : moveItemStackTo(stack, 0, graveSlots, false);

        if (!moved) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }
}
