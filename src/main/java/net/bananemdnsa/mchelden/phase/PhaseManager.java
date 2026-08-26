package net.bananemdnsa.mchelden.phase;

import javax.annotation.Nullable;

import net.bananemdnsa.mchelden.network.NetworkHandler;
import net.bananemdnsa.mchelden.state.GameState;
import net.bananemdnsa.mchelden.state.Phase;
import net.bananemdnsa.mchelden.text.HeldenText;

import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Phasenwechsel und ihre Seiteneffekte.
 *
 * <p>Einziger Weg, die Phase zu aendern. Der Wechsel ist mehr als eine Zahl im Speicher:
 * an ihm haengen Zeitlimit, Trennwand und Safezone. Liefe er an mehreren Stellen, koennte
 * eine davon einen Seiteneffekt vergessen.
 *
 * <p>Vorwaerts bekommt der Wechsel einen Countdown und eine Ansage — es ist der groesste
 * Moment des Projekts. Rueckwaerts nur eine Chat-Zeile: ein Zuruecksetzen ist eine
 * Korrektur durch Ops, kein Ereignis.
 *
 * <p><b>Das Zeitlimit steht bewusst nicht hier.</b> Es wird nicht geschaltet, sondern
 * abgefragt — siehe {@code PlaytimeTracker.isLimited}. Ein Schalter koennte von der Phase
 * abweichen, eine Frage nicht.
 */
public final class PhaseManager {
    /** Wie lange der Countdown vor einem Wechsel nach vorn laeuft. */
    public static final int COUNTDOWN_SECONDS = 5;

    @Nullable
    private static Phase pending;
    private static int ticksLeft;

    private PhaseManager() {
    }

    /**
     * Leitet einen Wechsel ein.
     *
     * <p>Nach vorn mit Countdown, zurueck sofort. Ein laufender Countdown wird dabei
     * ersetzt, damit ein vertippter Command nicht unabaenderlich ist.
     *
     * @return false, wenn die Phase schon anliegt
     */
    public static boolean begin(MinecraftServer server, Phase target) {
        Phase current = GameState.get(server).getPhase();
        if (current == target) {
            return false;
        }

        if (target.ordinal() < current.ordinal()) {
            apply(server, target, false);
            return true;
        }

        pending = target;
        ticksLeft = COUNTDOWN_SECONDS * 20;
        return true;
    }

    /** Die naechste Phase, oder {@code null}, wenn es keine mehr gibt. */
    @Nullable
    public static Phase next(MinecraftServer server) {
        return GameState.get(server).getPhase().next();
    }

    /** Laeuft gerade ein Countdown? */
    public static boolean isCountingDown() {
        return pending != null;
    }

    /** Bricht einen laufenden Countdown ab. */
    public static void cancel() {
        pending = null;
        ticksLeft = 0;
    }

    /** Treibt den Countdown voran. Aus dem Server-Tick aufrufen. */
    public static void tick(MinecraftServer server) {
        if (pending == null) {
            return;
        }

        ticksLeft--;
        if (ticksLeft <= 0) {
            Phase target = pending;
            pending = null;
            apply(server, target, true);
            return;
        }

        // Nur auf vollen Sekunden ansagen, nicht zwanzigmal pro Sekunde.
        if (ticksLeft % 20 != 0) {
            return;
        }

        int seconds = ticksLeft / 20;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitleTextPacket(
                    HeldenText.phaseCountdown(seconds)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(
                    HeldenText.phaseCountdownSubtitle(pending.getDisplayName())));

            // Steigende Tonhoehe: der Countdown zieht sich hoerbar zusammen.
            float step = (COUNTDOWN_SECONDS - seconds) / (float) COUNTDOWN_SECONDS;
            play(player, SoundEvents.NOTE_BLOCK_HAT.value(), 0.8f, 1.0f + step);
        }
    }

    /**
     * Schaltet die Phase und zieht alles nach, was daran haengt.
     *
     * <p>Etappe 7 haengt hier die Trennwand ein: {@code wall drop} beim Wechsel nach
     * Krieg, {@code wall raise} beim Zuruecksetzen nach Aufbau. Etappe 9 entsprechend die
     * Safezone und die Border.
     *
     * @param staged true fuer die volle Ansage, false fuer eine stille Korrektur
     */
    public static void apply(MinecraftServer server, Phase target, boolean staged) {
        GameState.get(server).setPhase(target);

        // Das Zeitlimit haengt an der Phase, nicht an einem Schalter. Der Sync bringt den
        // HUD-Zaehler damit von selbst in den richtigen Zustand — oder laesst ihn ganz weg.
        NetworkHandler.syncAll(server);

        if (staged) {
            announce(server, target);
        } else {
            server.getPlayerList().broadcastSystemMessage(
                    HeldenText.phaseReverted(target.getDisplayName()), false);
        }
    }

    /** Titel, tiefer Gong und eine Chat-Zeile mit dem, was sich konkret aendert. */
    private static void announce(MinecraftServer server, Phase target) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitleTextPacket(
                    HeldenText.phaseTitle(target.getDisplayName())));
            player.connection.send(new ClientboundSetSubtitleTextPacket(
                    HeldenText.phaseSubtitle(target)));

            // Derselbe tiefe Gong wie beim Einrasten des Bounty-Rades: was gross ist,
            // klingt in dieser Mod gleich.
            play(player, SoundEvents.ANVIL_LAND, 0.6f, 0.5f);
            play(player, SoundEvents.NOTE_BLOCK_BELL.value(), 1.2f, 0.5f);
            play(player, SoundEvents.TRIDENT_THUNDER.value(), 0.6f, 0.7f);
        }

        server.getPlayerList().broadcastSystemMessage(
                HeldenText.phaseChanged(target.getDisplayName()), false);
    }

    private static void play(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.playNotifySound(sound, SoundSource.MASTER, volume, pitch);
    }
}
