package net.bananemdnsa.mchelden.state;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Globaler Spielzustand. Aktuell nur die Phase, waechst mit den spaeteren Etappen. */
public class GameState extends SavedData {
    private static final String NAME = "mchelden_game";
    private static final String KEY_PHASE = "phase";

    private static final Factory<GameState> FACTORY =
            new Factory<>(GameState::new, GameState::load);

    private Phase phase = Phase.AUFBAU;

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

    public void reset() {
        setPhase(Phase.AUFBAU);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString(KEY_PHASE, phase.getId());
        return tag;
    }

    static GameState load(CompoundTag tag, HolderLookup.Provider registries) {
        GameState state = new GameState();
        state.phase = Phase.byId(tag.getString(KEY_PHASE));
        return state;
    }
}
