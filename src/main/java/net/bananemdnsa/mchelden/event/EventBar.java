package net.bananemdnsa.mchelden.event;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.bananemdnsa.mchelden.text.HeldenText;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/**
 * Die gruene Bossbar eines laufenden Events.
 *
 * <p>Sie <b>haelt sich selbst an der Spielerliste</b> statt bei Beitritt und Abgang
 * einzeln gepflegt zu werden: wer mitten im Event joint, sieht sie; wer geht, verschwindet
 * daraus. Das ist das, was diese Klasse selbst leistet.
 *
 * <p><b>Was angezeigt wird, weiss sie dagegen nicht.</b> Name, Restzeit und Fuellstand
 * reicht der Aufrufer bei jedem Tick herein — anders als bei der Bossbar des Final War,
 * die ihren Zustand selbst liest. Dass ein Event einen Serverneustart uebersteht, ist
 * deswegen eine Eigenschaft des Aufrufers und nicht dieser Klasse.
 *
 * <p>Gruen und nicht rot: der Final War ist eine Drohung, ein Event ist ein Geschenk.
 */
public final class EventBar {
    @Nullable
    private static ServerBossEvent bar;

    private EventBar() {
    }

    /** Zieht Beschriftung, Fuellstand und Zuschauerliste nach. */
    public static void update(MinecraftServer server, Component name, long remainingMillis,
                              float progress) {
        if (bar == null) {
            bar = new ServerBossEvent(
                    HeldenText.eventBar(name, remainingMillis),
                    BossEvent.BossBarColor.GREEN,
                    BossEvent.BossBarOverlay.PROGRESS);
        }

        bar.setName(HeldenText.eventBar(name, remainingMillis));
        bar.setProgress(progress);
        follow(server, bar);
    }

    /** Nimmt die Bossbar weg. */
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
