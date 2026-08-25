package net.bananemdnsa.mchelden.hearts;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.bananemdnsa.mchelden.network.NetworkHandler;
import net.bananemdnsa.mchelden.state.PlayerState;
import net.bananemdnsa.mchelden.state.PlayerStateStore;
import net.bananemdnsa.mchelden.text.HeldenText;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Einziger Weg, Herzen zu ändern. Alles läuft hier durch, damit der Deckel bei vier
 * und die Elimination bei null nirgends umgangen werden können.
 */
public final class HeartManager {
    /** Rein transient: geht ein Spieler beim Tod raus, braucht er beim Wiederkommen keine Animation. */
    private static final Set<UUID> PENDING_LOSS_ANIMATION = ConcurrentHashMap.newKeySet();

    private HeartManager() {
    }

    public static int get(MinecraftServer server, UUID uuid) {
        return PlayerStateStore.get(server).getOrCreate(uuid).getHearts();
    }

    /**
     * Setzt den Herzstand. Fällt er dabei auf null, scheidet der Spieler aus.
     *
     * @param killerName Name des Verursachers für die Ansage, leer wenn keiner
     * @return der tatsächlich gesetzte Stand nach dem Deckeln
     */
    public static int set(MinecraftServer server, UUID uuid, int hearts, String killerName) {
        PlayerStateStore store = PlayerStateStore.get(server);
        PlayerState state = store.getOrCreate(uuid);

        state.setHearts(hearts);
        store.setDirty();

        if (state.getHearts() <= 0 && !state.isEliminated()) {
            Elimination.eliminate(server, uuid, killerName);
        } else {
            sync(server, uuid);
        }
        return state.getHearts();
    }

    public static int add(MinecraftServer server, UUID uuid, int delta, String killerName) {
        return set(server, uuid, get(server, uuid) + delta, killerName);
    }

    /**
     * Herzverlust nach einem Tod durch einen Spieler.
     *
     * <p>Die Animation wird hier nur vorgemerkt und erst beim Respawn ausgelöst — im Moment
     * des Todes liegt der Todesbildschirm davor und niemand würde sie sehen.
     */
    public static void loseHeart(MinecraftServer server, UUID uuid, String killerName) {
        int remaining = add(server, uuid, -1, killerName);

        if (remaining > 0) {
            PENDING_LOSS_ANIMATION.add(uuid);
        }
    }

    /** Liefert true, wenn für diesen Spieler noch eine Verlust-Animation aussteht, und verbraucht sie. */
    public static boolean consumePendingLossAnimation(UUID uuid) {
        return PENDING_LOSS_ANIMATION.remove(uuid);
    }

    /** Herzgewinn durch den Bounty-Kill. Der Deckel bei vier greift im Setter. */
    public static void gainHeart(MinecraftServer server, UUID uuid) {
        int before = get(server, uuid);
        int after = add(server, uuid, 1, "");

        ServerPlayer player = online(server, uuid);
        if (player != null && after > before) {
            player.sendSystemMessage(HeldenText.heartGained(after));
        }
    }

    static void sync(MinecraftServer server, UUID uuid) {
        ServerPlayer player = online(server, uuid);
        if (player != null) {
            NetworkHandler.syncTo(player);
        }
    }

    @Nullable
    static ServerPlayer online(MinecraftServer server, UUID uuid) {
        return server.getPlayerList().getPlayer(uuid);
    }
}
