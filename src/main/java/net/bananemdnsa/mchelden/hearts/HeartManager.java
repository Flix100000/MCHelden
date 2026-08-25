package net.bananemdnsa.mchelden.hearts;

import java.util.UUID;

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

    /** Herzverlust nach einem Tod durch einen Spieler. */
    public static void loseHeart(MinecraftServer server, UUID uuid, String killerName) {
        int remaining = add(server, uuid, -1, killerName);

        ServerPlayer player = online(server, uuid);
        if (player != null && remaining > 0) {
            player.sendSystemMessage(HeldenText.heartLost(remaining));
        }
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
