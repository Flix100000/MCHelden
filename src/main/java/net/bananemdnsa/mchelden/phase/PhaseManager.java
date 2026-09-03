package net.bananemdnsa.mchelden.phase;

import javax.annotation.Nullable;

import net.bananemdnsa.mchelden.event.EventManager;
import net.bananemdnsa.mchelden.network.NetworkHandler;
import net.bananemdnsa.mchelden.network.SafeZoneShatterPayload;
import net.bananemdnsa.mchelden.state.GameState;
import net.bananemdnsa.mchelden.state.Phase;
import net.bananemdnsa.mchelden.text.HeldenText;
import net.bananemdnsa.mchelden.world.BorderController;
import net.bananemdnsa.mchelden.world.DividerWall;
import net.bananemdnsa.mchelden.world.FinalWarBar;
import net.bananemdnsa.mchelden.world.SafeZone;

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

    /**
     * Der Wechsel in den Krieg bekommt mehr Zeit.
     *
     * <p>In diesem Countdown laeuft die Partikelwelle des Wall Drops von 0,0 bis an beide
     * Enden der Trennlinie — zweitausend Bloecke wollen gesehen werden. Erst wenn sie
     * ankommt, faellt die Kollision.
     */
    public static final int WALL_DROP_SECONDS = DividerWall.DROP_TICKS / 20;

    /**
     * Der Final War bekommt zehn Sekunden.
     *
     * <p>In dieser Zeit zieht das Gewitter auf und die Kuppel gluecht sich zum Bruch hoch.
     * Fuenf Sekunden waeren zu wenig fuer beides — und anders als beim Wandfall haengt die
     * Zahl an keiner Bewegung, die genau ankommen muss.
     */
    public static final int FINAL_WAR_SECONDS = 10;

    /** Wie lange der Sturm nach dem Start noch steht: drei Minuten. */
    private static final int STORM_TICKS = 3 * 60 * 20;

    @Nullable
    private static Phase pending;
    private static int ticksLeft;

    /**
     * Die Dauer, mit der die Border anlaeuft.
     *
     * <p>Ein Feld und kein Parameter von {@link #apply}, weil {@code phase set finalwar}
     * und {@code phase next} denselben Weg nehmen und keine Dauer mitbringen. Ohne die
     * Vorgabe gaebe es einen Weg in den Final War, der die Border nicht startet.
     */
    private static long durationMillis = BorderController.DEFAULT_DURATION_MILLIS;

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
        return begin(server, target, BorderController.DEFAULT_DURATION_MILLIS);
    }

    /** Wie oben, mit der Dauer, ueber die die Border spaeter schrumpft. */
    public static boolean begin(MinecraftServer server, Phase target, long millis) {
        Phase current = GameState.get(server).getPhase();
        if (current == target) {
            return false;
        }

        durationMillis = millis;

        if (target.ordinal() < current.ordinal()) {
            apply(server, target, false);
            return true;
        }

        pending = target;
        ticksLeft = secondsFor(target) * 20;

        // Die Wand bricht waehrend des Countdowns auf, nicht erst an seinem Ende: Ansage,
        // Welle und das Verschwinden der Wand sind ein Vorgang.
        if (target == Phase.KRIEG && DividerWall.isUp(server)) {
            NetworkHandler.sendWallDrop(server, true);
        }

        // Beim Final War genauso: der Sturm zieht auf und die Kuppel gluecht sich hoch,
        // waehrend der Countdown laeuft. An seinem Ende steht der Bruch, nicht sein Beginn.
        if (target == Phase.FINAL_WAR) {
            raiseStorm(server);
            NetworkHandler.sendSafeZoneShatter(server, SafeZoneShatterPayload.Stage.ARM);
        }

        return true;
    }

    /** Wall Drop und Final War brauchen laenger als eine gewoehnliche Ansage. */
    private static int secondsFor(Phase target) {
        return switch (target) {
            case KRIEG -> WALL_DROP_SECONDS;
            case FINAL_WAR -> FINAL_WAR_SECONDS;
            default -> COUNTDOWN_SECONDS;
        };
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

    /**
     * Bricht einen laufenden Countdown ab und nimmt zurueck, was schon angelaufen ist.
     *
     * <p>Ohne das Zuruecknehmen bliebe nach einem abgebrochenen Final-War-Countdown ein
     * Sturm stehen und eine gluehende Kuppel, die nie zerbricht.
     */
    public static void cancel(MinecraftServer server) {
        if (pending == Phase.KRIEG) {
            NetworkHandler.sendWallDrop(server, false);
        }

        if (pending == Phase.FINAL_WAR) {
            clearStorm(server);
            NetworkHandler.sendSafeZoneShatter(server, SafeZoneShatterPayload.Stage.CANCEL);
        }

        pending = null;
        ticksLeft = 0;
    }

    /** Treibt den Countdown voran. Aus dem Server-Tick aufrufen. */
    public static void tick(MinecraftServer server) {
        if (pending == null) {
            return;
        }

        ticksLeft--;

        // Die Welle laeuft waehrend des Countdowns, nicht danach: Ansage und Wall Drop
        // sind ein Moment, nicht zwei.
        if (pending == Phase.KRIEG) {
            DividerWall.tickDrop(server, secondsFor(pending) * 20 - ticksLeft);
        }

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
            float step = (secondsFor(pending) - seconds) / (float) secondsFor(pending);
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
        Phase previous = GameState.get(server).getPhase();
        GameState.get(server).setPhase(target);

        // Ein Event, das in der neuen Phase nichts mehr bewirkt, endet mit ihr: eine
        // Bossbar, die fuer nichts herunterzaehlt, ist Rauschen.
        EventManager.endIfPhaseLeft(server, target);

        // Die Trennwand haengt an der Phase, ist aber ein eigener Schalter: `wall drop` und
        // `wall raise` sollen auch unabhaengig greifen. Der Wechsel bedient ihn, besitzt
        // ihn nicht.
        DividerWall.setUp(server, target == Phase.AUFBAU);
        if (staged && target == Phase.KRIEG) {
            DividerWall.playForEveryone(server);
        }

        // Der Final War ist mehr als eine Zahl im Speicher: die Border laeuft an, die Kuppel
        // zerbricht. Die Safezone selbst braucht nichts — sie fragt die Phase ab.
        if (target == Phase.FINAL_WAR) {
            BorderController.startFinalWar(server, durationMillis);
            NetworkHandler.sendSafeZoneShatter(server, SafeZoneShatterPayload.Stage.BREAK);
            SafeZone.burst(server);
        } else if (previous == Phase.FINAL_WAR) {
            // Nur beim Zuruecknehmen aus dem Final War heraus. Ein Wechsel von Aufbau nach
            // Krieg darf ein von Hand gesetztes Border-Ziel nicht wegraeumen.
            BorderController.reset(server);
            clearStorm(server);
            FinalWarBar.hide();

            // Und die Truemmer raeumen: wer waehrend der fuenf Sekunden Scherbenflug
            // zuruecknimmt, haette sonst eine Zone, die wieder gilt, aber noch als Bruch
            // gezeichnet wird.
            NetworkHandler.sendSafeZoneShatter(server, SafeZoneShatterPayload.Stage.CANCEL);
        }

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

            // Der Final War bekommt einen Einschlag obendrauf: er ist neben der Bossbar der
            // einzige Beat, den wirklich jeder mitbekommt, egal wo er steht.
            if (target == Phase.FINAL_WAR) {
                play(player, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);
            }
        }

        server.getPlayerList().broadcastSystemMessage(
                HeldenText.phaseChanged(target.getDisplayName()), false);
    }

    private static void play(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.playNotifySound(sound, SoundSource.MASTER, volume, pitch);
    }

    /**
     * Laesst weltweit ein Gewitter losbrechen.
     *
     * <p>Der einzige Beat, den zwanzig ueber 4000 mal 4000 verteilte Spieler gleichzeitig
     * <em>sehen</em>. Die Kuppel sieht nur, wer bei 0,0 steht — dasselbe Problem wie bei
     * der verworfenen Welle aus Etappe 7, nur ohne Loesung: ein Zylinder mit fuenfzig
     * Bloecken Radius laesst sich nicht so inszenieren, dass ihn die ganze Welt sieht.
     *
     * <p>Nach drei Minuten laeuft die Wetterzeit ab und Vanilla wuerfelt neu: der Sturm
     * markiert den Moment und geht dann. Ueber zwei Stunden waere Regen nur noch
     * Sichtbehinderung im PvP.
     */
    private static void raiseStorm(MinecraftServer server) {
        server.overworld().setWeatherParameters(0, STORM_TICKS, true, true);
    }

    private static void clearStorm(MinecraftServer server) {
        server.overworld().setWeatherParameters(STORM_TICKS, 0, false, false);
    }
}
