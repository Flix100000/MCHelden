package net.bananemdnsa.mchelden.playtime;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.bananemdnsa.mchelden.combat.CombatTracker;
import net.bananemdnsa.mchelden.combat.HitTimer;
import net.bananemdnsa.mchelden.event.EventManager;
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
 *
 * <p>Gemessen wird an der Wanduhr, nicht an Serverticks. Warum das ein Unterschied ist,
 * steht an {@link #LAST_CHARGED}.
 *
 * <p>Dieselbe Ueberlegung traegt das Event {@code notimelimit}: auch dort wird nichts
 * geschaltet, sondern gefragt. Nach dem Eventende gilt das Limit im naechsten Tick wieder,
 * ohne dass irgendwer es zurueckstellen muesste — und auch die Kampf-Kulanz weiter unten
 * faengt dann neu an, weil ein angefangener Ueberzug eine Zeit ohne Limit nicht uebersteht.
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
     *
     * <p>Gezaehlt in echten Sekunden, waehrend der Combat-Timer an Ticks haengt: unter Last
     * kann der Kampf damit laenger laufen als der Aufschub reicht. Genau richtig — der
     * Deckel ist eine Obergrenze und keine Zusage.
     */
    public static final int MAX_OVERRUN_SECONDS = HitTimer.MAX_TICKS / 20;

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

    /**
     * Wann fuer einen Spieler das letzte Mal abgerechnet wurde.
     *
     * <p><b>Warum nicht einfach Serverticks gezaehlt werden:</b> Serverticks sind kein
     * Zeitmass. Unter Last kommen weniger als zwanzig pro Sekunde; einen kleinen Rueckstand
     * holt der Server wieder auf, sobald er aber mehr als zwei Sekunden hinterherhaengt,
     * verwirft er die faelligen Ticks — {@code Can't keep up!} im Log —, und die sind
     * endgueltig weg. Ein Kontingent, das an Ticks haengt, verliert damit genau diese Zeit,
     * waehrend die Anzeige oben rechts in echter Zeit zaehlt und einen Sync nur bei
     * Aenderungen bekommt. Ueber eine Sitzung werden daraus Minuten: die Uhr im HUD stand
     * auf Null, waehrend der Server noch Kontingent uebrig hatte und seine Vorwarnungen
     * hinterherschickte.
     *
     * <p>Rein transient wie {@link #OVERRUN}: der Anker gilt fuer die laufende Sitzung, und
     * wer nicht da ist, verbraucht keine Zeit.
     */
    private static final Map<UUID, Long> LAST_CHARGED = new ConcurrentHashMap<>();

    /**
     * Wie viele Abrechnungen zwischen zwei Korrekturen der Anzeige liegen.
     *
     * <p>Die Uhr oben rechts zaehlt selbst herunter und bekommt sonst nur bei Aenderungen
     * einen Stand vom Server. Beide messen jetzt echte Zeit, laufen also zusammen — aber
     * ein Client, der selbst Ticks verliert, liefe ueber eine Stunde genauso auseinander
     * wie vorher der Server, nur in die andere Richtung: der Kick kaeme vor der Null. Eine
     * genaue Zahl alle dreissig Sekunden deckelt das, und weil sie zur Sekunde des Servers
     * springt, ist die Korrektur nicht zu sehen.
     */
    private static final int SYNC_GAP = 30;

    /** Zaehlt die Abrechnungen bis zur naechsten Korrektur. */
    private static int syncCounter;

    /**
     * Wie oft nachgerechnet wird: einmal pro Sekunde genuegt, nicht zwanzigmal.
     *
     * <p>Nur der Takt, nicht das Mass — <i>wie viel</i> abgerechnet wird, sagt die Uhr.
     * Faellt der Takt unter Last aus, holt die naechste Abrechnung das Versaeumte nach.
     */
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

    /**
     * Wie viele ganze Sekunden seit dem Anker verstrichen sind.
     *
     * <p>Nie negativ, obwohl die Uhr monoton laeuft: eine Rechnung, die Zeit verschenkt,
     * soll auch dann nichts verschenken, wenn sie einmal falsch herum aufgerufen wird.
     */
    static int elapsedSeconds(long anchorMillis, long nowMillis) {
        return (int) Math.max(0L, (nowMillis - anchorMillis) / 1000L);
    }

    /**
     * Wohin der Anker wandert, wenn diese Sekunden abgerechnet sind.
     *
     * <p>Um die abgerechneten Sekunden weiter statt auf jetzt: der angebrochene Rest bleibt
     * stehen. Sonst verlore jede Abrechnung bis zu einer knappen Sekunde, und aus den Resten
     * wuerde derselbe Rueckstand, den das Zaehlen von Ticks schon hatte.
     */
    static long advanceAnchor(long anchorMillis, int chargedSeconds) {
        return anchorMillis + chargedSeconds * 1000L;
    }

    /**
     * Die Uhr, an der das Kontingent haengt.
     *
     * <p>Monoton statt {@code currentTimeMillis}: gemessen werden Abstaende innerhalb einer
     * Sitzung, und eine nachgestellte Systemuhr — ein NTP-Sprung genuegt — darf weder eine
     * Stunde verschenken noch den Anker in eine Zukunft setzen, die nie kommt.
     */
    private static long nowMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    /** Was die Anzeige zeigen soll, oder {@link #NO_LIMIT}. */
    public static int displayRemaining(MinecraftServer server, ServerPlayer player, PlayerState state) {
        return isTracked(server, player)
                ? remainingSeconds(state.getPlaytimeUsedSeconds())
                : NO_LIMIT;
    }

    /**
     * Gehoert dieser Spieler ueberhaupt zum Kontingent — unabhaengig davon, ob ein laufendes
     * Event die Uhr gerade anhaelt?
     *
     * <p>Auf einem echten Server fuer alle, Ops eingeschlossen — eine Stunde am Tag ist
     * eine Spielregel, keine Frage des Ranges.
     *
     * <p><b>Einzelspieler-Welten sind ausgenommen.</b> Dort gibt es keine Konsole, ueber
     * die jemand nachhelfen koennte: wer sich einmal aussperrt, kaeme bis vier Uhr morgens
     * nicht mehr in seine eigene Welt. Zum Ansehen laesst sich das Limit dort mit
     * {@code /helden debug playtime} trotzdem einschalten.
     */
    private static boolean isTracked(MinecraftServer server, ServerPlayer player) {
        if (GameState.get(server).getPhase() != Phase.AUFBAU) {
            return false;
        }

        return FORCED.contains(player.getUUID()) || !server.isSingleplayer();
    }

    /**
     * Gilt das Limit fuer diesen Spieler gerade?
     *
     * <p>Auf einem echten Server fuer alle, Ops eingeschlossen — eine Stunde am Tag ist
     * eine Spielregel, keine Frage des Ranges.
     *
     * <p><b>Einzelspieler-Welten sind ausgenommen.</b> Dort gibt es keine Konsole, ueber
     * die jemand nachhelfen koennte: wer sich einmal aussperrt, kaeme bis vier Uhr morgens
     * nicht mehr in seine eigene Welt. Zum Ansehen laesst sich das Limit dort mit
     * {@code /helden debug playtime} trotzdem einschalten.
     *
     * <p>Ein laufendes Zeit-Event haelt die Uhr zusaetzlich an. Aus dieser einen Antwort
     * faellt alles Weitere von selbst: keine Sekunde wird verbraucht, niemand wird gekickt,
     * und wer sein Kontingent heute schon leer hat, kommt beim Join trotzdem herein — ohne
     * das waere ein Abendevent fuer die Haelfte des Servers wertlos.
     */
    public static boolean isLimited(MinecraftServer server, ServerPlayer player) {
        return isTracked(server, player) && !EventManager.suspendsPlaytime(server);
    }

    /**
     * Steht die Uhr gerade still, weil ein Event sie anhaelt?
     *
     * <p>Fuer das HUD: es soll die Zahl weiter zeigen, nur grau und ohne sich zu bewegen,
     * statt ganz zu verschwinden.
     */
    public static boolean isPaused(MinecraftServer server, ServerPlayer player) {
        return isTracked(server, player) && EventManager.suspendsPlaytime(server);
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
        LAST_CHARGED.remove(uuid);
        NetworkHandler.syncTo(player);
        return forced;
    }

    /** Raeumt auf, was nur fuer diese Sitzung galt. */
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        FORCED.remove(event.getEntity().getUUID());
        OVERRUN.remove(event.getEntity().getUUID());
        LAST_CHARGED.remove(event.getEntity().getUUID());
        net.bananemdnsa.mchelden.world.SafeZone.forget(event.getEntity().getUUID());
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

        boolean correct = ++syncCounter >= SYNC_GAP;
        if (correct) {
            syncCounter = 0;
        }

        long today = playDay(LocalDateTime.now());
        PlayerStateStore store = PlayerStateStore.get(server);

        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            tickPlayer(server, store, player, today, correct);
        }
    }

    private static void tickPlayer(MinecraftServer server, PlayerStateStore store,
                                   ServerPlayer player, long today, boolean correct) {
        PlayerState state = store.getOrCreate(player.getUUID());

        if (rollOver(store, state, today)) {
            player.sendSystemMessage(HeldenText.playtimeReset());
            NetworkHandler.syncTo(player);
        }

        if (!isLimited(server, player)) {
            // Der Ueberzug gehoert zum geltenden Limit. Bliebe er ueber ein Event stehen,
            // floege jemand mit fast aufgebrauchter Kulanz eine Sekunde nach dem Eventende
            // wortlos aus dem Kampf — genau der Fall, den die Kulanz verhindern soll.
            OVERRUN.remove(player.getUUID());
            // Der Anker faellt mit, sonst stuende die Pause bei der naechsten Abrechnung
            // rueckwirkend als verbrauchte Zeit da.
            LAST_CHARGED.remove(player.getUUID());
            return;
        }

        int elapsed = charge(player);
        if (elapsed > 0 && !spend(server, store, state, player, elapsed)) {
            return;
        }

        // Nach dem Abrechnen, nicht davor: sonst bekaeme der Client die Sekunde, die gerade
        // verbraucht wird, noch einmal geschenkt und laege dauerhaft eine Sekunde vor dem
        // Server — der Kick kaeme dann bei 0:01.
        if (correct) {
            NetworkHandler.syncTo(player);
        }
    }

    /**
     * Schreibt die verbrauchten Sekunden fest, warnt an den Schwellen und wirft notfalls
     * hinaus.
     *
     * @return false, wenn der Spieler damit draussen ist
     */
    private static boolean spend(MinecraftServer server, PlayerStateStore store,
                                 PlayerState state, ServerPlayer player, int elapsed) {
        int before = remainingSeconds(state.getPlaytimeUsedSeconds());
        state.setPlaytimeUsedSeconds(state.getPlaytimeUsedSeconds() + elapsed);
        store.setDirty();
        int after = remainingSeconds(state.getPlaytimeUsedSeconds());

        warn(player, before, after);

        return after > 0 || !kickOrDefer(server, player, elapsed);
    }

    /**
     * Nimmt die faelligen Sekunden von der Uhr und setzt den Anker weiter.
     *
     * <p>Die erste Abrechnung einer Sitzung rechnet nichts ab, sondern setzt nur den Anker:
     * die Zeit vor dem Join gehoert niemandem.
     *
     * @return die abzurechnenden Sekunden, meist eine, nach einem Lag-Spike mehrere
     */
    private static int charge(ServerPlayer player) {
        long now = nowMillis();
        Long anchor = LAST_CHARGED.putIfAbsent(player.getUUID(), now);
        if (anchor == null) {
            return 0;
        }

        int elapsed = elapsedSeconds(anchor, now);
        if (elapsed > 0) {
            LAST_CHARGED.put(player.getUUID(), advanceAnchor(anchor, elapsed));
        }
        return elapsed;
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
     *
     * @return true, wenn tatsaechlich gekickt wurde
     */
    private static boolean kickOrDefer(MinecraftServer server, ServerPlayer player, int elapsed) {
        UUID uuid = player.getUUID();

        if (CombatTracker.isInCombat(uuid)) {
            int overrun = OVERRUN.merge(uuid, elapsed, Integer::sum);
            // Genau die eben abgerechneten Sekunden: dann stand vorher nichts im Eintrag.
            if (overrun == elapsed) {
                player.sendSystemMessage(HeldenText.playtimeAfterCombat());
            }

            // Danach faellt der Aufschub weg, auch wenn der Kampf weitergeht: jeder Treffer
            // fuellt den Combat-Timer wieder auf, der Kampf koennte also nie enden.
            if (overrun < MAX_OVERRUN_SECONDS) {
                return false;
            }
        }

        OVERRUN.remove(uuid);
        player.connection.disconnect(HeldenText.playtimeKick());
        return true;
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
