package net.bananemdnsa.mchelden.state;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Globaler Spielzustand. Aktuell nur die Phase, waechst mit den spaeteren Etappen. */
public class GameState extends SavedData {

    private static final String NAME = "mchelden_game";
    private static final String KEY_PHASE = "phase";
    private static final String KEY_WALL_UP = "wallUp";
    private static final String KEY_BORDER_SET = "borderSet";
    private static final String KEY_CENTER_X = "centerX";
    private static final String KEY_CENTER_Z = "centerZ";
    private static final String KEY_EVENT_ID = "eventId";
    private static final String KEY_EVENT_STARTED = "eventStartedAt";
    private static final String KEY_EVENT_ENDS = "eventEndsAt";

    private static final Factory<GameState> FACTORY =
            new Factory<>(GameState::new, GameState::load);

    private Phase phase = Phase.AUFBAU;

    /**
     * Steht die Trennwand?
     *
     * <p>Ein gespeicherter Schalter, kein aus der Phase abgeleiteter Wert — anders als das
     * Zeitlimit. Grund: {@code wall drop} und {@code wall raise} sollen laut Spec auch
     * unabhaengig von der Phase greifen. Der Phasenwechsel bedient den Schalter, besitzt
     * ihn aber nicht.
     */
    private boolean wallUp = true;

    /**
     * Hat die Mod die Weltborder schon einmal auf ihre Groesse gesetzt?
     *
     * <p>Ein gespeicherter Schalter, weil die Antwort sich nicht ableiten laesst: eine
     * Border von 4000 kann heissen "die Mod hat sie gesetzt" oder "ein Op hat sie zufaellig
     * genau dorthin gesetzt".
     *
     * <p><b>Nur einmal.</b> Bei jedem Start zu setzen wuerde einen laufenden Final War
     * zurueckwerfen und jedes von Hand gesetzte Ziel wegraeumen — ein Serverneustart mitten
     * im Schrumpfen liefe sonst gegen die Wiederaufnahme, die Vanilla gratis mitbringt.
     */
    private boolean borderSet;

    /**
     * Mitte der Arena: Safezone, Trennwand und Weltborder haengen daran.
     *
     * <p>Gespeichert, weil sich der Wert aus nichts ableiten laesst — und weil ein Verlust
     * teuer waere: nach einem Neustart stuenden Safezone und Wand wieder bei 0,0, waehrend
     * die Weltborder ihre verschobene Mitte behaelt. Die Wand laege dann nicht mehr in der
     * Mitte, und eine Seite haette mehr Land als die andere.
     */
    private double centerX;
    private double centerZ;

    /**
     * Das laufende Event: Kennung, Start und Ende.
     *
     * <p>Leere Kennung heisst "keins". Gespeichert, weil ein Event einen Serverneustart
     * ueberstehen muss — sonst frisst ein Neustart um 20:30 ein Event, das bis 21:00 laufen
     * sollte.
     *
     * <p>Gerechnet in Millisekunden der <b>Wanduhr</b>, nicht in Serverticks: waehrend der
     * Downtime spielt ohnehin niemand, und ein Event soll dann enden, wann es enden sollte.
     * Beide Zeitpunkte werden gebraucht — der Balken braucht die Gesamtdauer, sonst wuesste
     * er nicht, wie voll voll ist.
     *
     * <p>Die Kennung steht hier als Zeichenkette und nicht als {@code EventType}, damit der
     * Spielzustand nichts vom Paket {@code event} wissen muss. Aufgeloest wird sie im
     * {@code EventManager}.
     */
    private String eventId = "";
    private long eventStartedAt;
    private long eventEndsAt;

    public static GameState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
        setDirty();
    }

    public boolean isWallUp() {
        return wallUp;
    }

    public boolean isBorderSet() {
        return borderSet;
    }

    public void setBorderSet(boolean borderSet) {
        this.borderSet = borderSet;
        setDirty();
    }

    public void setWallUp(boolean wallUp) {
        this.wallUp = wallUp;
        setDirty();
    }

    public double getCenterX() {
        return centerX;
    }

    public double getCenterZ() {
        return centerZ;
    }

    public void setCenter(double centerX, double centerZ) {
        this.centerX = centerX;
        this.centerZ = centerZ;
        setDirty();
    }

    /** Die Kennung des laufenden Events, oder leer. */
    public String getEventId() {
        return eventId;
    }

    public long getEventStartedAt() {
        return eventStartedAt;
    }

    public long getEventEndsAt() {
        return eventEndsAt;
    }

    public void setEvent(String eventId, long startedAt, long endsAt) {
        this.eventId = eventId;
        this.eventStartedAt = startedAt;
        this.eventEndsAt = endsAt;
        setDirty();
    }

    public void clearEvent() {
        setEvent("", 0L, 0L);
    }

    public void reset() {
        setPhase(Phase.AUFBAU);
        setWallUp(true);
        clearEvent();
        // Die Arenamitte bleibt stehen — aus demselben Grund wie die Bordergroesse unten:
        // wo die Arena liegt, ist Weltaufbau und nicht der Zustand einer Runde. Zurueck in
        // die Weltmitte holt sie "/helden center reset".
        // Nicht der Schalter wird zurueckgesetzt, sondern die Border selbst — siehe
        // BorderController.reset. Ein zurueckgesetzter Schalter wuerde sie beim naechsten
        // Start ein zweites Mal setzen, ohne dass jemand darum gebeten haette.
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString(KEY_PHASE, phase.getId());
        tag.putBoolean(KEY_WALL_UP, wallUp);
        tag.putBoolean(KEY_BORDER_SET, borderSet);
        tag.putDouble(KEY_CENTER_X, centerX);
        tag.putDouble(KEY_CENTER_Z, centerZ);
        tag.putString(KEY_EVENT_ID, eventId);
        tag.putLong(KEY_EVENT_STARTED, eventStartedAt);
        tag.putLong(KEY_EVENT_ENDS, eventEndsAt);
        return tag;
    }

    static GameState load(CompoundTag tag, HolderLookup.Provider registries) {
        GameState state = new GameState();
        state.phase = Phase.bySavedId(tag.getString(KEY_PHASE));
        // Ohne gespeicherten Eintrag steht die Wand: eine frische Welt beginnt im Aufbau.
        state.wallUp = !tag.contains(KEY_WALL_UP) || tag.getBoolean(KEY_WALL_UP);
        // Ohne Eintrag: noch nicht gesetzt. Bestehende Welten bekommen ihre Border damit
        // beim naechsten Start, statt sie fuer immer von Hand zu brauchen.
        state.borderSet = tag.getBoolean(KEY_BORDER_SET);
        // Ohne Eintrag die Weltmitte: eine Welt von vor dieser Aenderung liegt auf 0,0.
        state.centerX = tag.getDouble(KEY_CENTER_X);
        state.centerZ = tag.getDouble(KEY_CENTER_Z);
        // Ohne Eintrag: kein Event. `getString` liefert dafuer die leere Zeichenkette.
        state.eventId = tag.getString(KEY_EVENT_ID);
        state.eventStartedAt = tag.getLong(KEY_EVENT_STARTED);
        state.eventEndsAt = tag.getLong(KEY_EVENT_ENDS);
        return state;
    }
}
