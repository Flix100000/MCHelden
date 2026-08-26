package net.bananemdnsa.mchelden.hearts;

import java.util.UUID;

import net.bananemdnsa.mchelden.bounty.BountyManager;
import net.bananemdnsa.mchelden.state.PlayerState;
import net.bananemdnsa.mchelden.state.PlayerStateStore;
import net.bananemdnsa.mchelden.text.HeldenText;

import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Ausscheiden und Zurückholen.
 *
 * <p>Bewusst kein Vanilla-Ban: die Banlist bleibt für echte Regelverstöße frei, und ein
 * fälschlich Ausgeschiedener ist ein Command entfernt statt ein Eingriff in eine JSON-Datei.
 */
public final class Elimination {
    private Elimination() {
    }

    public static boolean isEliminated(MinecraftServer server, UUID uuid) {
        PlayerState state = PlayerStateStore.get(server).find(uuid);
        return state != null && state.isEliminated();
    }

    public static void eliminate(MinecraftServer server, UUID uuid, String killerName) {
        PlayerStateStore store = PlayerStateStore.get(server);
        PlayerState state = store.getOrCreate(uuid);

        if (state.isEliminated()) {
            return;
        }

        state.setEliminated(true);
        state.setHearts(0);
        store.setDirty();

        // Der Bounty-Partner muss es im HUD sehen: sein viertes Herz ist ab jetzt
        // unerreichbar, es gibt kein Ersatzziel.
        BountyManager.syncPartnerOf(server, uuid);

        announce(server, state.getName(), killerName, store.countAlive());

        ServerPlayer player = HeartManager.online(server, uuid);
        if (player != null) {
            player.connection.disconnect(HeldenText.eliminationKick());
        }
    }

    /** Holt einen Ausgeschiedenen mit dem angegebenen Herzstand zurück ins Spiel. */
    public static void revive(MinecraftServer server, UUID uuid, int hearts) {
        PlayerStateStore store = PlayerStateStore.get(server);
        PlayerState state = store.getOrCreate(uuid);

        state.setEliminated(false);
        state.setHearts(Math.max(1, hearts));
        store.setDirty();

        HeartManager.sync(server, uuid);
        BountyManager.syncPartnerOf(server, uuid);
    }

    private static void announce(MinecraftServer server, String victim, String killer, int alive) {
        String name = victim.isEmpty() ? "Ein Spieler" : victim;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitleTextPacket(HeldenText.eliminationTitle(name)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(HeldenText.eliminationSubtitle(killer)));
        }

        server.getPlayerList().broadcastSystemMessage(HeldenText.survivorCount(alive), false);
    }
}
