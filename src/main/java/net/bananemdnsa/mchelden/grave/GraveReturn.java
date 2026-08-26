package net.bananemdnsa.mchelden.grave;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Die Hälfte, die der Spieler behalten darf, zwischen Tod und Respawn.
 *
 * <p>Beim Tod ist das Inventar bereits geleert, beim Respawn wird es neu aufgebaut — dazwischen
 * muss der Anteil irgendwo liegen. Transient, denn wer sich zwischen Tod und Respawn ausloggt,
 * kommt ohnehin über den Combat-Log-Weg zurück.
 */
public final class GraveReturn {
    private static final Map<UUID, List<ItemStack>> PENDING = new ConcurrentHashMap<>();

    private GraveReturn() {
    }

    public static void remember(UUID uuid, List<ItemStack> stacks) {
        if (!stacks.isEmpty()) {
            PENDING.put(uuid, new ArrayList<>(stacks));
        }
    }

    /** Legt dem Spieler seinen Anteil ins Inventar. Was nicht passt, fällt vor ihm auf den Boden. */
    public static void deliver(ServerPlayer player) {
        List<ItemStack> stacks = PENDING.remove(player.getUUID());
        if (stacks == null) {
            return;
        }

        for (ItemStack stack : stacks) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    public static void forget(UUID uuid) {
        PENDING.remove(uuid);
    }
}
