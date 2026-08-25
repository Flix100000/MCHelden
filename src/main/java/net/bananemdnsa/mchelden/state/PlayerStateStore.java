package net.bananemdnsa.mchelden.state;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Zentrale Ablage aller Spielerzustaende, ueber UUIDs adressiert.
 *
 * <p>Bewusst eine SavedData statt Attachments am Spieler: der Eliminationsstatus muss
 * beim Join geprueft werden, bevor der Spieler existiert, {@code /helden info} soll fuer
 * Ausgeschiedene funktionieren, und die Bounty-Paarung muss auch Offline-Spieler erfassen.
 */
public class PlayerStateStore extends SavedData {
    private static final String NAME = "mchelden_players";
    private static final String KEY_PLAYERS = "players";

    private static final Factory<PlayerStateStore> FACTORY =
            new Factory<>(PlayerStateStore::new, PlayerStateStore::load);

    private final Map<UUID, PlayerState> states = new HashMap<>();

    public static PlayerStateStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /** Liefert den Zustand oder {@code null}, wenn der Spieler dem Server nie begegnet ist. */
    @Nullable
    public PlayerState find(UUID uuid) {
        return states.get(uuid);
    }

    /** Liefert den Zustand und legt ihn bei Bedarf mit den Startwerten an. */
    public PlayerState getOrCreate(UUID uuid) {
        PlayerState state = states.get(uuid);
        if (state == null) {
            state = new PlayerState(uuid);
            states.put(uuid, state);
            setDirty();
        }
        return state;
    }

    public Collection<PlayerState> all() {
        return states.values();
    }

    /** Anzahl der noch nicht ausgeschiedenen Spieler. Speist den Ueberlebenden-Counter. */
    public int countAlive() {
        return (int) states.values().stream().filter(state -> !state.isEliminated()).count();
    }

    public void remove(UUID uuid) {
        if (states.remove(uuid) != null) {
            setDirty();
        }
    }

    public void clear() {
        states.clear();
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (PlayerState state : states.values()) {
            list.add(state.save());
        }
        tag.put(KEY_PLAYERS, list);
        return tag;
    }

    static PlayerStateStore load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerStateStore store = new PlayerStateStore();
        ListTag list = tag.getList(KEY_PLAYERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            PlayerState state = PlayerState.load(list.getCompound(i));
            store.states.put(state.getUuid(), state);
        }
        return store;
    }
}
