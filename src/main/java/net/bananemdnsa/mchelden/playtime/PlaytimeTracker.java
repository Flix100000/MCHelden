package net.bananemdnsa.mchelden.playtime;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.bananemdnsa.mchelden.combat.CombatTracker;
import net.bananemdnsa.mchelden.network.NetworkHandler;
import net.bananemdnsa.mchelden.state.GameState;
import net.bananemdnsa.mchelden.state.Phase;
import net.bananemdnsa.mchelden.state.PlayerState;
import net.bananemdnsa.mchelden.state.PlayerStateStore;
import net.bananemdnsa.mchelden.text.HeldenText;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Das Spielzeit-Kontingent der Aufbauphase: eine Stunde pro Tag.
 *
 * <p>Zweck ist Fairness — niemand soll am ersten Tag zwoelf Stunden grinden, waehrend der
 * Rest in Leder herumlaeuft.
 *
 * <p>Ob das Limit gilt, ist bewusst kein gespeicherter Schalter, sondern eine Frage an die
 * Phase. Dadurch funktioniert die Gegenrichtung von selbst — {@code phase set aufbau}
 * bringt das Limit zurueck —, und es kann keinen Zustand geben, in dem Phase und Limit
 * auseinanderliegen.
 */
public final class PlaytimeTracker {
    /**
     * Wann ein neuer Spieltag beginnt.
     *
     * <p>Vier Uhr statt Mitternacht, damit niemand um 23:30 seine halbe Stunde spielt und
     * um 00:00 eine volle neue bekommt.
     */
    public static final int RESET_HOUR = 4;

    /** Kennzeichnet "kein Limit" fuer die Anzeige: Ops und alle Phasen ausser Aufbau. */
    public static final int NO_LIMIT = -1;

    /** Vorwarnungen, in Sekunden Restzeit. */
    private static final int[] WARNINGS = {600, 300, 60};

    /**
     * Wie lange ein Kampf den Kick hoechstens aufschieben kann.
     *
     * <p>Gebunden an den Deckel des Combat-Timers: laenger als drei Minuten kann ein Kampf
     * nicht dauern, ohne dass neu zugeschlagen wird. Ohne diese Grenze waere der Aufschub
     * unbegrenzt — zwei Leute, die sich abwechselnd anticken, koennten jemanden beliebig
     * lange online halten, und genau das soll das Zeitlimit verhindern.
     */
    public static final int MAX_OVERRUN_SECONDS = CombatTracker.MAX_TICKS / 20;

    /**
     * Wer beim Ablaufen im Kampf stand, und seit wie vielen Sekunden.
     *
     * <p>Rein transient: wer sich in dem Moment ausloggt, ist ohnehin draussen — und beim
     * Wiederkommen greift die Pruefung beim Join.
     */
    private static final Map<UUID, Integer> OVERRUN = new ConcurrentHashMap<>();

    /**
     * Spieler, die das Limit zum Testen eingeschaltet haben, obwohl es hier nicht gilt.
     *
     * <p>Ohne das liesse sich die ganze Etappe im Einzelspieler nicht ansehen — und der
     * Einzelspieler ist genau der Ort, an dem geprueft wird.
     *
     * <p>Wird beim Ausloggen vergessen. Genau das macht die Sache sicher: wer sich damit
     * selbst hinauswirft, kommt beim naechsten Versuch wieder herein.
     */
    private static final Set<UUID> FORCED = ConcurrentHashMap.newKeySet();

    /** Der Server tickt zwanzigmal pro Sekunde, gezaehlt wird aber in Sekunden. */
    private static int tickCounter;

    private PlaytimeTracker() {
    }

    /**
     * Welchem Spieltag ein Zeitpunkt zugerechnet wird.
     *
     * <p>Gerechnet ueber die lokale Zeit des Servers: die Grenze verschiebt sich damit mit
     * der Sommerzeit, und es gibt keinen Tag mit zwei Resets.
     */
    public static long playDay(LocalDateTime now) {
        return now.minusHours(RESET_HOUR).toLocalDate().toEpochDay();
    }

    /**
     * Was vom Tageskontingent uebrig ist.
     *
     * <p>Nie negativ: ein Kampf am Ende der Stunde kann die verbrauchte Zeit ueber das
     * Kontingent treiben, weil der Kick dort auf den Combat-Timer wartet.
     */
    public static int remainingSeconds(int usedSeconds) {
        return Math.max(0, PlayerState.DAILY_PLAYTIME_SECONDS - usedSeconds);
    }

    /** Was die Anzeige zeigen soll, oder {@link #NO_LIMIT}. */
    public static int displayRemaining(MinecraftServer server, ServerPlayer player, PlayerState state) {
        return isLimited(server, player)
                ? remainingSeconds(state.getPlaytimeUsedSeconds())
                : NO_LIMIT;
    }

    /**
     * Gilt das Limit fuer diesen Spieler?
     *
     * <p>Auf einem echten Server fuer alle, Ops eingeschlossen — eine Stunde am Tag ist
     * eine Spielregel, keine Frage des Ranges.
     *
     * <p><b>Einzelspieler-Welten sind ausgenommen.</b> Dort gibt es keine Konsole, ueber
     * die jemand nachhelfen koennte: wer sich einmal aussperrt, kaeme bis vier Uhr morgens
     * nicht mehr in seine eigene Welt. Zum Ansehen laesst sich das Limit dort mit
     * {@code /helden debug playtime} trotzdem einschalten.
     */
    public static boolean isLimited(MinecraftServer server, ServerPlayer player) {
        if (GameState.get(server).getPhase() != Phase.AUFBAU) {
            return false;
        }
        return FORCED.contains(player.getUUID()) || !server.isSingleplayer();
    }

    /**
     * Schaltet die Op-Ausnahme fuer einen Spieler ab oder wieder an.
     *
     * @return true, wenn das Limit jetzt gilt
     */
    public static boolean toggleForced(ServerPlayer player) {
        UUID uuid = player.getUUID();

        boolean forced = !FORCED.remove(uuid);
        if (forced) {
            FORCED.add(uuid);
        }

        OVERRUN.remove(uuid);
        NetworkHandler.syncTo(player);
        return forced;
    }

    /** Raeumt auf, was nur fuer diese Sitzung galt. */
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        FORCED.remove(event.getEntity().getUUID());
        OVERRUN.remove(event.getEntity().getUUID());
    }

    /**
     * Zaehlt die Spielzeit aller Anwesenden herunter. Aus dem Server-Tick aufrufen.
     *
     * <p>Die Spielerliste wird kopiert, weil hier gekickt wird — waehrend der Schleife
     * daraus zu entfernen waere ein Fehler, den man erst beim ersten echten Kick saehe.
     */
    public static void tick(MinecraftServer server) {
        if (++tickCounter < 20) {
            return;
        }
        tickCounter = 0;

        long today = playDay(LocalDateTime.now());
        PlayerStateStore store = PlayerStateStore.get(server);

        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            tickPlayer(server, store, player, today);
        }
    }

    private static void tickPlayer(MinecraftServer server, PlayerStateStore store,
                                   ServerPlayer player, long today) {
        PlayerState state = store.getOrCreate(player.getUUID());

        if (rollOver(store, state, today)) {
            player.sendSystemMessage(HeldenText.playtimeReset());
            NetworkHandler.syncTo(player);
        }

        if (!isLimited(server, player)) {
            return;
        }

        int before = remainingSeconds(state.getPlaytimeUsedSeconds());
        state.setPlaytimeUsedSeconds(state.getPlaytimeUsedSeconds() + 1);
        store.setDirty();
        int after = remainingSeconds(state.getPlaytimeUsedSeconds());

        warn(player, before, after);

        if (after <= 0) {
            kickOrDefer(server, player);
        }
    }

    /**
     * Setzt das Kontingent zurueck, wenn ein neuer Spieltag begonnen hat.
     *
     * <p>Laeuft auch fuer Ops und ausserhalb der Aufbauphase: der gespeicherte Spieltag
     * muss aktuell bleiben, sonst gaebe es beim Ruecksetzen der Phase einen Schwall
     * nachgeholter Resets.
     *
     * @return true, wenn zurueckgesetzt wurde
     */
    private static boolean rollOver(PlayerStateStore store, PlayerState state, long today) {
        if (state.getPlaytimeResetDay() == today) {
            return false;
        }

        boolean hadUsedTime = state.getPlaytimeUsedSeconds() > 0;
        state.setPlaytimeResetDay(today);
        state.setPlaytimeUsedSeconds(0);
        store.setDirty();
        return hadUsedTime;
    }

    /**
     * Warnt beim Unterschreiten einer Schwelle.
     *
     * <p>Am Uebergang gemessen, nicht an der Gleichheit: sonst verschluckt ein
     * {@code time add} die Warnung, weil die Restzeit ueber der Schwelle springt.
     */
    private static void warn(ServerPlayer player, int before, int after) {
        for (int threshold : WARNINGS) {
            if (before > threshold && after <= threshold) {
                player.sendSystemMessage(HeldenText.playtimeWarning(threshold / 60));
            }
        }
    }

    /**
     * Wirft raus — oder wartet, wenn der Spieler gerade kaempft.
     *
     * <p>Sofortiger Kick waere ein legaler Combat-Log. Maximal drei Minuten Ueberzug, und
     * man muss dafuer in einem echten Kampf stehen.
     */
    private static void kickOrDefer(MinecraftServer server, ServerPlayer player) {
        UUID uuid = player.getUUID();

        if (CombatTracker.isInCombat(uuid)) {
            int overrun = OVERRUN.merge(uuid, 1, Integer::sum);
            if (overrun == 1) {
                player.sendSystemMessage(HeldenText.playtimeAfterCombat());
            }

            // Danach faellt der Aufschub weg, auch wenn der Kampf weitergeht: jeder Treffer
            // fuellt den Combat-Timer wieder auf, der Kampf koennte also nie enden.
            if (overrun < MAX_OVERRUN_SECONDS) {
                return;
            }
        }

        OVERRUN.remove(uuid);
        player.connection.disconnect(HeldenText.playtimeKick());
    }

    /**
     * Weist beim Join ab, wer sein Kontingent schon aufgebraucht hat.
     *
     * @return true, wenn der Spieler abgewiesen wurde
     */
    public static boolean onJoin(MinecraftServer server, ServerPlayer player) {
        PlayerStateStore store = PlayerStateStore.get(server);
        PlayerState state = store.getOrCreate(player.getUUID());
        rollOver(store, state, playDay(LocalDateTime.now()));

        if (!isLimited(server, player) || remainingSeconds(state.getPlaytimeUsedSeconds()) > 0) {
            return false;
        }

        player.connection.disconnect(HeldenText.playtimeKick());
        return true;
    }

    /** Schenkt Zeit dazu. Negative Werte nehmen welche weg. */
    public static void addSeconds(MinecraftServer server, UUID uuid, int seconds) {
        PlayerStateStore store = PlayerStateStore.get(server);
        PlayerState state = store.getOrCreate(uuid);

        state.setPlaytimeUsedSeconds(state.getPlaytimeUsedSeconds() - seconds);
        finish(server, store, uuid);
    }

    /** Setzt die verbleibende Zeit auf einen festen Wert. */
    public static void setRemaining(MinecraftServer server, UUID uuid, int seconds) {
        PlayerStateStore store = PlayerStateStore.get(server);
        PlayerState state = store.getOrCreate(uuid);

        state.setPlaytimeUsedSeconds(PlayerState.DAILY_PLAYTIME_SECONDS - seconds);
        finish(server, store, uuid);
    }

    /**
     * Haelt den Tag fest und schickt den neuen Stand.
     *
     * <p>Der Spieltag wird mitgeschrieben, damit geschenkte Zeit nicht Sekunden spaeter
     * vom faelligen Reset wieder eingesammelt wird.
     */
    private static void finish(MinecraftServer server, PlayerStateStore store, UUID uuid) {
        store.getOrCreate(uuid).setPlaytimeResetDay(playDay(LocalDateTime.now()));
        store.setDirty();

        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            OVERRUN.remove(uuid);
            NetworkHandler.syncTo(player);
        }
    }

    /** Wie viel Zeit ein Spieler noch hat, auch offline. */
    public static int remainingFor(MinecraftServer server, UUID uuid) {
        PlayerState state = PlayerStateStore.get(server).find(uuid);
        return state == null
                ? PlayerState.DAILY_PLAYTIME_SECONDS
                : remainingSeconds(state.getPlaytimeUsedSeconds());
    }
}
