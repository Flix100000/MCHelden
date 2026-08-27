package net.bananemdnsa.mchelden.grave;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Wo Graeber stehen und wem sie gehoeren.
 *
 * <p><b>Warum ueberhaupt gespeichert?</b> Graeber sind Bloecke in der Welt, und in einem
 * ungeladenen Chunk findet man sie ohne Aufzeichnung gar nicht. Das ist einer von zwei
 * Werten dieser Mod, die sich nicht ableiten lassen — der andere ist der Border-Schalter
 * im {@code GameState}. Ueberall sonst gilt hier: lieber fragen als speichern.
 *
 * <p>Das Verzeichnis darf hinterherhinken. Ein Eintrag ohne Block schadet nichts: beim
 * Abraeumen faellt er still mit heraus. Es muss also nie von Hand gepflegt werden.
 */
public class GraveRegistry extends SavedData {

    private static final String NAME = "mchelden_graves";
    private static final String KEY_GRAVES = "graves";
    private static final String KEY_POS = "pos";
    private static final String KEY_OWNER = "owner";

    private static final Factory<GraveRegistry> FACTORY =
            new Factory<>(GraveRegistry::new, GraveRegistry::load);

    /** Reihenfolge bleibt erhalten, damit ein Abraeumen immer gleich ablaeuft. */
    private final Map<BlockPos, UUID> graves = new LinkedHashMap<>();

    public static GraveRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /** Traegt ein Grab ein. Eine Stelle kann nur ein Grab tragen. */
    public void add(BlockPos pos, UUID owner) {
        graves.put(pos.immutable(), owner);
        setDirty();
    }

    /** Traegt ein Grab aus. Ein zweites Mal ist harmlos. */
    public void remove(BlockPos pos) {
        if (graves.remove(pos) != null) {
            setDirty();
        }
    }

    /** Alle bekannten Graeber. */
    public List<BlockPos> all() {
        return new ArrayList<>(graves.keySet());
    }

    /** Die Graeber eines Spielers. */
    public List<BlockPos> of(UUID owner) {
        List<BlockPos> found = new ArrayList<>();
        for (Map.Entry<BlockPos, UUID> entry : graves.entrySet()) {
            if (owner.equals(entry.getValue())) {
                found.add(entry.getKey());
            }
        }
        return found;
    }

    public void clear() {
        graves.clear();
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();

        for (Map.Entry<BlockPos, UUID> entry : graves.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putLong(KEY_POS, entry.getKey().asLong());
            one.putUUID(KEY_OWNER, entry.getValue());
            list.add(one);
        }

        tag.put(KEY_GRAVES, list);
        return tag;
    }

    static GraveRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        GraveRegistry registry = new GraveRegistry();
        ListTag list = tag.getList(KEY_GRAVES, Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag one = list.getCompound(i);
            registry.graves.put(BlockPos.of(one.getLong(KEY_POS)), one.getUUID(KEY_OWNER));
        }

        return registry;
    }
}
