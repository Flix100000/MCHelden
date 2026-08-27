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

    public void setWallUp(boolean wallUp) {
        this.wallUp = wallUp;
        setDirty();
    }

    public void reset() {
        setPhase(Phase.AUFBAU);
        setWallUp(true);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString(KEY_PHASE, phase.getId());
        tag.putBoolean(KEY_WALL_UP, wallUp);
        return tag;
    }

    static GameState load(CompoundTag tag, HolderLookup.Provider registries) {
        GameState state = new GameState();
        state.phase = Phase.byId(tag.getString(KEY_PHASE));
        // Ohne gespeicherten Eintrag steht die Wand: eine frische Welt beginnt im Aufbau.
        state.wallUp = !tag.contains(KEY_WALL_UP) || tag.getBoolean(KEY_WALL_UP);
        return state;
    }
}
