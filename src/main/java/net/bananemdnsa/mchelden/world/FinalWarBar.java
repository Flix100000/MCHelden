package net.bananemdnsa.mchelden.world;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.bananemdnsa.mchelden.state.GameState;
import net.bananemdnsa.mchelden.state.Phase;
import net.bananemdnsa.mchelden.text.HeldenText;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/**
 * Die rote Bossbar waehrend des Final War.
 *
 * <p><b>Sie baut sich jede Sekunde aus dem Serverzustand neu auf</b>, statt bei Start,
 * Beitritt und Ende einzeln gepflegt zu werden. Damit erledigen sich drei Faelle von
 * selbst: wer mitten im Final War joint, sieht sie; wer geht, verschwindet daraus; und
 * nach einem Serverneustart steht sie wieder da, weil die Phase gespeichert ist und die
 * Border ihren Lauf von allein fortsetzt.
 *
 * <p>Der Balken ist die Arena: voll bei 2000, leer bei 160. Beide Zahlen daneben kommen
 * frisch aus der Border, es gibt also keinen Wert, der davonlaufen koennte.
 */
public final class FinalWarBar {
    /** Die Anzeige wird einmal pro Sekunde nachgezogen, nicht zwanzigmal. */
    private static final int UPDATE_GAP = 20;

    @Nullable
    private static ServerBossEvent bar;

    private FinalWarBar() {
    }

    /** Aus dem Servertick aufrufen. */
    public static void tick(MinecraftServer server) {
        if (GameState.get(server).getPhase() != Phase.FINAL_WAR) {
            hide();
            return;
        }

        if (server.getTickCount() % UPDATE_GAP != 0) {
            return;
        }

        double size = BorderController.size(server);
        long remaining = BorderController.remainingMillis(server);

        if (bar == null) {
            bar = new ServerBossEvent(
                    HeldenText.finalWarBar(remaining),
                    BossEvent.BossBarColor.RED,
                    BossEvent.BossBarOverlay.PROGRESS);
        }

        bar.setName(HeldenText.finalWarBar(remaining));
        bar.setProgress(BorderController.progress(size));
        follow(server, bar);
    }

    /** Nimmt die Bossbar weg. Beim Zuruecknehmen des Final War. */
    public static void hide() {
        if (bar != null) {
            bar.removeAllPlayers();
            bar = null;
        }
    }

    /**
     * Haelt die Zuschauerliste am Spielerstand.
     *
     * <p>{@code addPlayer} auf jemanden, der schon drinsteht, kostet nichts — die Liste ist
     * ein Set und verschickt nur bei echter Aenderung ein Paket.
     */
    private static void follow(MinecraftServer server, ServerBossEvent event) {
        Set<ServerPlayer> online = new HashSet<>(server.getPlayerList().getPlayers());

        for (ServerPlayer gone : List.copyOf(event.getPlayers())) {
            if (!online.contains(gone)) {
                event.removePlayer(gone);
            }
        }

        for (ServerPlayer player : online) {
            event.addPlayer(player);
        }
    }
}
