package net.bananemdnsa.mchelden.network;

import java.util.Optional;
import java.util.UUID;

import net.bananemdnsa.mchelden.MCHelden;
import net.bananemdnsa.mchelden.bounty.BountyManager;
import net.bananemdnsa.mchelden.combat.CombatTracker;
import net.bananemdnsa.mchelden.combat.ItemQuota;
import net.bananemdnsa.mchelden.playtime.PlaytimeTracker;
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
        registrar.playToClient(
                CombatSyncPayload.TYPE,
                CombatSyncPayload.STREAM_CODEC,
                NetworkHandler::handleCombatOnClient);
        registrar.playToClient(
                BountyRollPayload.TYPE,
                BountyRollPayload.STREAM_CODEC,
                NetworkHandler::handleBountyRollOnClient);
        registrar.playToClient(
                RenderDebugPayload.TYPE,
                RenderDebugPayload.STREAM_CODEC,
                NetworkHandler::handleRenderDebugOnClient);
        registrar.playToClient(
                WallDropPayload.TYPE,
                WallDropPayload.STREAM_CODEC,
                NetworkHandler::handleWallDropOnClient);
        registrar.playToClient(
                SafeZoneShatterPayload.TYPE,
                SafeZoneShatterPayload.STREAM_CODEC,
                NetworkHandler::handleSafeZoneShatterOnClient);
        registrar.playToClient(
                EliminationPayload.TYPE,
                EliminationPayload.STREAM_CODEC,
                NetworkHandler::handleEliminationOnClient);
    }

    /**
     * Schickt Timer und Kontingente. Nur bei Aenderungen — den Timer zaehlt der Client selbst,
     * und die Kontingente aendern sich nur, wenn tatsaechlich etwas verbraucht wird.
     */
    public static void sendCombat(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new CombatSyncPayload(
                CombatTracker.remainingTicks(player.getUUID()),
                ItemQuota.remaining(player.getUUID(), ItemQuota.Kind.PEARL),
                ItemQuota.remaining(player.getUUID(), ItemQuota.Kind.COBWEB)));
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
                bountyView(server, store, state),
                PlaytimeTracker.displayRemaining(server, player, state),
                GameState.get(server).getPhase().getId(),
                GameState.get(server).isWallUp(),
                new ArenaView(GameState.get(server).getCenterX(),
                        GameState.get(server).getCenterZ())));
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncTo(player);
        }
    }

    /**
     * Baut die Bounty-Sicht fuer das HUD.
     *
     * <p>Ob das Ziel ausgeschieden ist, steht nirgends gespeichert — es wird hier aus dem
     * Zustand des Ziels abgelesen. Ein {@code /helden revive} hebt die Ausgrauung damit von
     * selbst wieder auf, ohne dass ein zweiter Wert nachgefuehrt werden muesste.
     */
    private static BountyView bountyView(MinecraftServer server, PlayerStateStore store,
                                         PlayerState state) {
        // Wer sein Gluecksrad noch vor sich hat, bekommt vorerst gar kein Bounty zu sehen —
        // sonst stuende das Ergebnis oben links, bevor der Streifen es preisgibt.
        if (BountyManager.isRollPending(server, state.getUuid())) {
            return BountyView.NONE;
        }

        UUID target = state.getBountyTarget();
        if (target == null) {
            return new BountyView("", Optional.empty(), state.isBountyResolved(), false);
        }

        PlayerState targetState = store.find(target);
        return new BountyView(
                targetState != null ? targetState.getName() : "",
                Optional.of(target),
                false,
                targetState != null && targetState.isEliminated());
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

    private static void handleCombatOnClient(CombatSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> net.bananemdnsa.mchelden.client.ClientState.onCombat(payload));
    }

    private static void handleBountyRollOnClient(BountyRollPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> net.bananemdnsa.mchelden.client.ClientState.onBountyRoll(payload));
    }

    private static void handleEliminationOnClient(EliminationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> net.bananemdnsa.mchelden.client.ClientState.onElimination(
                payload.victim(), payload.killer()));
    }

    private static void handleRenderDebugOnClient(RenderDebugPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            net.bananemdnsa.mchelden.client.render.WallRenderer.report();
            net.bananemdnsa.mchelden.client.render.SafeZoneRenderer.report();
        });
    }

    private static void handleWallDropOnClient(WallDropPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                net.bananemdnsa.mchelden.client.ClientState.onWallDrop(payload.dropping()));
    }

    /** Laesst die Trennwand bei allen aufbrechen, oder bricht den Vorgang ab. */
    public static void sendWallDrop(MinecraftServer server, boolean dropping) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, new WallDropPayload(dropping));
        }
    }

    private static void handleSafeZoneShatterOnClient(SafeZoneShatterPayload payload,
                                                      IPayloadContext context) {
        context.enqueueWork(() ->
                net.bananemdnsa.mchelden.client.ClientState.onSafeZoneShatter(payload.stage()));
    }

    /** Laesst die Safezone-Kuppel bei allen aufziehen, zerspringen oder zur Ruhe kommen. */
    public static void sendSafeZoneShatter(MinecraftServer server,
                                           SafeZoneShatterPayload.Stage stage) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, new SafeZoneShatterPayload(stage));
        }
    }

    /** Fragt einen Client, was er ueber Trennwand und Safezone weiss. */
    /**
     * Sagt allen, dass jemand ausgeschieden ist.
     *
     * <p>An alle, nicht nur an die Beteiligten: wer noch im Spiel ist, soll mitbekommen,
     * dass ein Gegner weniger unterwegs ist.
     */
    public static void sendElimination(MinecraftServer server, String victim, String killer) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, new EliminationPayload(victim, killer));
        }
    }

    public static void askRenderReport(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new RenderDebugPayload());
    }

    public static String version() {
        return MCHelden.NETWORK_VERSION;
    }
}
