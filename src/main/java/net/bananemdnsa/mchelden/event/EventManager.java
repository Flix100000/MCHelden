package net.bananemdnsa.mchelden.event;

import javax.annotation.Nullable;

import net.bananemdnsa.mchelden.network.NetworkHandler;
import net.bananemdnsa.mchelden.state.GameState;
import net.bananemdnsa.mchelden.state.Phase;
import net.bananemdnsa.mchelden.text.DurationText;
import net.bananemdnsa.mchelden.text.HeldenText;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * Der Lebenszyklus eines Events: starten, ticken, enden.
 *
 * <p>Der Manager haelt selbst keinen gespeicherten Zustand ueber das laufende Event —
 * der steht im {@code GameState}. Daraus folgt, dass ein Neustart nichts kostet: die
 * Bossbar baut sich aus demselben Zustand wieder auf.
 */
public final class EventManager {

    /** Wie lange vor Schluss gewarnt wird. */
    private static final int WARNING_SECONDS = 60;

    /** Ab hier tickt es jede Sekunde hoerbar. */
    private static final int COUNTDOWN_SECONDS = 10;

    /** Die Anzeige wird einmal pro Sekunde nachgezogen, nicht zwanzigmal. */
    private static final int UPDATE_GAP = 20;

    /**
     * Die zuletzt gemessene Restsekunde.
     *
     * <p>Verhindert, dass dieselbe gesampelte Sekunde zweimal einen Ton ausloest. Sie
     * traegt zugleich den vorherigen Wert weiter, damit die Warnung an ihrem Uebergang
     * gemessen werden kann statt an einer Gleichheit — siehe {@link #tick}.
     */
    private static int lastSeconds = -1;

    /**
     * Der Endzeitpunkt des zuletzt gesehenen Events, oder 0.
     *
     * <p>Ein boolesches "schon gesehen" reichte nicht: es ist statisch und uebersteht im
     * Einzelspieler den Wechsel in eine andere Welt. Der Endzeitpunkt kommt dagegen aus dem
     * Zustand der Welt selbst — ein anderes Event ist damit automatisch ein unbekanntes,
     * ohne dass der Manager wissen muesste, auf welchem Server er laeuft.
     *
     * <p>Woran das haengt: ein Event, dessen Ende waehrend eines Serverausfalls verstrichen
     * ist, wird still geraeumt statt mit Titel und Gong nachgefeiert.
     */
    private static long seenEndsAt;

    private EventManager() {
    }

    /** Wie viel Zeit noch laeuft. Nie negativ. */
    public static long remainingMillis(long endsAt, long now) {
        return Math.max(0L, endsAt - now);
    }

    /**
     * Wie voll der Balken steht: 1 am Anfang, 0 am Ende.
     *
     * <p>Ein Fenster ohne Dauer gilt als vorbei und nicht als unendlich — sonst stuende ein
     * kaputter gespeicherter Zustand als voller Balken da, der nie leer wird.
     */
    public static float progress(long startedAt, long endsAt, long now) {
        long total = endsAt - startedAt;
        if (total <= 0L) {
            return 0.0f;
        }
        return Mth.clamp(remainingMillis(endsAt, now) / (float) total, 0.0f, 1.0f);
    }

    /** Der laufende Eventtyp, oder {@code null}. */
    @Nullable
    public static EventType active(MinecraftServer server) {
        return EventType.byId(GameState.get(server).getEventId());
    }

    /** Laeuft gerade ein Event, das die Spielzeit anhaelt? */
    public static boolean suspendsPlaytime(MinecraftServer server) {
        return active(server) == EventType.NO_TIME_LIMIT;
    }

    /** Wie lange das laufende Event noch laeuft. Ohne Event: 0. */
    public static long remainingMillis(MinecraftServer server) {
        return remainingMillis(GameState.get(server).getEventEndsAt(), System.currentTimeMillis());
    }

    /**
     * Startet ein Event und ersetzt dabei ein laufendes.
     *
     * <p><b>Ersetzen statt ablehnen</b>, aus demselben Grund wie beim Phasen-Countdown: ein
     * vertipptes {@code 1h} statt {@code 1m} darf nicht unumkehrbar sein.
     *
     * @return false, wenn der Typ in der aktuellen Phase nicht startbar ist
     */
    public static boolean start(MinecraftServer server, EventType type, long millis) {
        GameState state = GameState.get(server);
        if (state.getPhase() != type.allowedPhase()) {
            return false;
        }

        boolean replaced = active(server) != null;
        long now = System.currentTimeMillis();
        state.setEvent(type.getId(), now, now + millis);

        lastSeconds = -1;

        // Das Zeitlimit haengt an einer Frage, nicht an einem Schalter — der Sync bringt
        // den HUD-Zaehler damit von selbst in den richtigen Zustand oder laesst ihn weg.
        NetworkHandler.syncAll(server);

        announceStart(server, type, millis, replaced);
        return true;
    }

    /**
     * Beendet das laufende Event.
     *
     * @param broadcast die Zeile fuer alle, oder {@code null} fuer ein stilles Ende —
     *                  {@code reset all} ist eine Korrektur und keine Ansage
     * @return false, wenn gar keins lief
     */
    public static boolean stop(MinecraftServer server, @Nullable Component broadcast) {
        GameState state = GameState.get(server);
        if (state.getEventId().isEmpty()) {
            return false;
        }

        state.clearEvent();
        EventBar.hide();
        lastSeconds = -1;
        seenEndsAt = 0L;

        if (broadcast != null) {
            server.getPlayerList().broadcastSystemMessage(broadcast, false);
        }

        NetworkHandler.syncAll(server);
        return true;
    }

    /**
     * Beendet ein Event, das in der neuen Phase nichts mehr bewirkt.
     *
     * <p>Aus {@code PhaseManager.apply} aufgerufen. Ohne das zaehlte nach einem Wechsel in
     * den Krieg eine Bossbar fuer nichts herunter.
     */
    public static void endIfPhaseLeft(MinecraftServer server, Phase target) {
        EventType running = active(server);
        if (running != null && running.allowedPhase() != target) {
            stop(server, HeldenText.eventEndedByPhase(running.getDisplayName()));
        }
    }

    /**
     * Bossbar, Warnung, Countdown und Ende. Aus dem Servertick aufrufen.
     *
     * <p>Muss <b>vor</b> {@code PlaytimeTracker.tick} laufen: in dem Tick, in dem das Event
     * endet, soll das Limit schon wieder gelten.
     */
    public static void tick(MinecraftServer server) {
        GameState state = GameState.get(server);
        EventType type = EventType.byId(state.getEventId());

        if (type == null) {
            // Deckt beides ab: kein Event, und eine Speicherdatei mit unbekannter Kennung.
            if (!state.getEventId().isEmpty()) {
                state.clearEvent();
            }
            EventBar.hide();
            seenEndsAt = 0L;
            lastSeconds = -1;
            return;
        }

        if (server.getTickCount() % UPDATE_GAP != 0) {
            return;
        }

        long now = System.currentTimeMillis();
        long remaining = remainingMillis(state.getEventEndsAt(), now);

        if (seenEndsAt != state.getEventEndsAt()) {
            // Ein anderes Event als beim letzten Blick: frisch gestartet, aus der
            // Speicherdatei geladen, oder eine andere Welt im selben Spielstart.
            seenEndsAt = state.getEventEndsAt();
            lastSeconds = -1;

            if (remaining <= 0L) {
                stop(server, null);
                return;
            }
        }

        if (remaining <= 0L) {
            finish(server, type);
            return;
        }

        EventBar.update(server, type.getDisplayName(), remaining,
                progress(state.getEventStartedAt(), state.getEventEndsAt(), now));

        int seconds = Mth.ceil(remaining / 1000.0f);
        if (seconds == lastSeconds) {
            return;
        }

        int previous = lastSeconds;
        lastSeconds = seconds;

        // Am Uebergang gemessen, nicht an der Gleichheit: der Tick haengt an Serverticks,
        // die Restzeit an der Wanduhr. Unter Last kann eine Sekunde uebersprungen werden,
        // und die Warnung ist die einzige, die ein Spieler vor dem Kick bekommt.
        if (previous > WARNING_SECONDS && seconds <= WARNING_SECONDS) {
            server.getPlayerList().broadcastSystemMessage(
                    HeldenText.eventWarning(type.getDisplayName(), WARNING_SECONDS), false);
        }

        if (seconds <= COUNTDOWN_SECONDS) {
            // Steigende Tonhoehe wie beim Phasen-Countdown: es zieht sich hoerbar zusammen.
            float step = (COUNTDOWN_SECONDS - seconds) / (float) COUNTDOWN_SECONDS;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                play(player, SoundEvents.NOTE_BLOCK_HAT.value(), 0.8f, 1.0f + step);
            }
        }
    }

    /** Titel, Gong und die Chat-Zeile beim Start. */
    private static void announceStart(MinecraftServer server, EventType type, long millis,
                                      boolean replaced) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitleTextPacket(
                    HeldenText.eventTitle(type.getDisplayName())));
            player.connection.send(new ClientboundSetSubtitleTextPacket(
                    HeldenText.eventSubtitle(type)));

            // Derselbe Gong wie beim Phasenwechsel: was gross ist, klingt in dieser Mod gleich.
            play(player, SoundEvents.ANVIL_LAND, 0.6f, 0.5f);
            play(player, SoundEvents.NOTE_BLOCK_BELL.value(), 1.2f, 0.5f);
        }

        if (replaced) {
            server.getPlayerList().broadcastSystemMessage(HeldenText.eventReplaced(), false);
        }

        server.getPlayerList().broadcastSystemMessage(
                HeldenText.eventStarted(type.getDisplayName(), DurationText.clock(millis)), false);
    }

    /** Das regulaere Ende: Einblendung, Ton, Chat-Zeile. */
    private static void finish(MinecraftServer server, EventType type) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitleTextPacket(HeldenText.eventEndTitle()));
            // Der Untertitel wird ueberschrieben und nicht geleert: sonst stuende der vom
            // Start noch darunter — und wer sein Kontingent aufgebraucht hat, soll hier
            // erfahren, dass die Uhr wieder laeuft, statt es am Kick zu merken.
            player.connection.send(new ClientboundSetSubtitleTextPacket(
                    HeldenText.eventEndSubtitle(type)));
            play(player, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 0.7f);
        }

        stop(server, HeldenText.eventEnded(type.getDisplayName()));
    }

    private static void play(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.playNotifySound(sound, SoundSource.MASTER, volume, pitch);
    }
}
