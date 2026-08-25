package net.bananemdnsa.mchelden.network;

import java.util.UUID;

import net.bananemdnsa.mchelden.MCHelden;
import net.bananemdnsa.mchelden.state.GameState;
import net.bananemdnsa.mchelden.state.PlayerState;
import net.bananemdnsa.mchelden.state.PlayerStateStore;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NetworkHandler {
    private NetworkHandler() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                StateSyncPayload.TYPE,
                StateSyncPayload.STREAM_CODEC,
                NetworkHandler::handleStateOnClient);
        registrar.playToClient(
                HeartLostPayload.TYPE,
                HeartLostPayload.STREAM_CODEC,
                NetworkHandler::handleHeartLostOnClient);
    }

    /** Startet beim Empfänger die Verlust-Animation. Beim Respawn schicken, nicht beim Tod. */
    public static void sendHeartLost(ServerPlayer player, int remaining) {
        PacketDistributor.sendToPlayer(player, new HeartLostPayload(remaining));
    }

    /** Schickt einem Spieler seinen eigenen Zustand. Nach jeder Aenderung aufrufen. */
    public static void syncTo(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        PlayerStateStore store = PlayerStateStore.get(server);
        PlayerState state = store.getOrCreate(player.getUUID());
        state.setName(player.getGameProfile().getName());

        PacketDistributor.sendToPlayer(player, new StateSyncPayload(
                state.getHearts(),
                resolveBountyName(store, state.getBountyTarget()),
                state.isBountyResolved(),
                Math.max(0, PlayerState.DAILY_PLAYTIME_SECONDS - state.getPlaytimeUsedSeconds()),
                GameState.get(server).getPhase().getId()));
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncTo(player);
        }
    }

    private static String resolveBountyName(PlayerStateStore store, UUID target) {
        if (target == null) {
            return "";
        }
        PlayerState targetState = store.find(target);
        return targetState != null ? targetState.getName() : "";
    }

    /**
     * Laeuft ausschliesslich auf dem Client. Die Client-Klasse wird erst beim ersten
     * Aufruf geladen, nicht bei der Registrierung — auf dedizierten Servern also nie.
     */
    private static void handleStateOnClient(StateSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> net.bananemdnsa.mchelden.client.ClientState.accept(payload));
    }

    private static void handleHeartLostOnClient(HeartLostPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> net.bananemdnsa.mchelden.client.ClientState.onHeartLost(payload.remaining()));
    }

    public static String version() {
        return MCHelden.NETWORK_VERSION;
    }
}
