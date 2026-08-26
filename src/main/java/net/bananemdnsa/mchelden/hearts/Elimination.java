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
import net.minecraft.world.level.GameType;

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
            remove(server, player);
        }
    }

    /**
     * Nimmt einen Ausgeschiedenen aus dem Spiel.
     *
     * <p>Auf dem Server heisst das rauswerfen. In einer Einzelspieler-Welt waere das
     * sinnlos — man kann niemanden aus seiner eigenen Welt aussperren, und beim naechsten
     * Betreten stuende dieselbe Entscheidung wieder an. Dort greift stattdessen das, was
     * Minecraft im Hardcore-Modus tut: zusehen duerfen, mitspielen nicht.
     */
    public static void remove(MinecraftServer server, ServerPlayer player) {
        if (server.isSingleplayer()) {
            player.setGameMode(GameType.SPECTATOR);
            player.sendSystemMessage(HeldenText.eliminationSpectator());
            return;
        }

        player.connection.disconnect(HeldenText.eliminationKick());
    }

    /** Holt einen Ausgeschiedenen mit dem angegebenen Herzstand zurück ins Spiel. */
    public static void revive(MinecraftServer server, UUID uuid, int hearts) {
        PlayerStateStore store = PlayerStateStore.get(server);
        PlayerState state = store.getOrCreate(uuid);

        state.setEliminated(false);
        state.setHearts(Math.max(1, hearts));
        store.setDirty();

        // Wer im Einzelspieler nur zusehen durfte, darf jetzt wieder mitspielen.
        ServerPlayer player = HeartManager.online(server, uuid);
        if (player != null && player.isSpectator()) {
            player.setGameMode(GameType.SURVIVAL);
        }

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
