package net.bananemdnsa.mchelden.grave;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Wo Graeber stehen, wem sie gehoeren und wann sie entstanden sind.
 *
 * <p><b>Warum ueberhaupt gespeichert?</b> Graeber sind Bloecke in der Welt, und in einem
 * ungeladenen Chunk findet man sie ohne Aufzeichnung gar nicht. Das ist einer von zwei
 * Werten dieser Mod, die sich nicht ableiten lassen — der andere ist der Border-Schalter
 * im {@code GameState}. Ueberall sonst gilt hier: lieber fragen als speichern.
 *
 * <p><b>Der Zeitstempel ist die einzige Zutat, die hinzukommen durfte.</b> Er beantwortet
 * „welches Grab ist das neueste" und „wie alt ist dieses" ohne einen Blockzugriff — sonst
 * muesste {@code /helden grave list} fuer jede Zeile einen Chunk laden. Und er ist der
 * einzige Wert, der dafuer taugt: einmal beim Eintragen gesetzt, danach unveraenderlich,
 * kann also nicht veralten. Item-Anzahl und XP koennen es und bleiben deswegen draussen.
 *
 * <p>Das Verzeichnis darf hinterherhinken. Ein Eintrag ohne Block schadet nichts: beim
 * Abraeumen faellt er still mit heraus. Es muss also nie von Hand gepflegt werden.
 */
public class GraveRegistry extends SavedData {

    /**
     * Zeitstempel eines Eintrags aus einer Welt von vor dieser Aufzeichnung.
     *
     * <p>Sortiert nach hinten, was fuer ein altes Grab richtig ist, und wird angezeigt als
     * „Zeitpunkt unbekannt" statt als Zahl aus der Luft.
     */
    public static final long UNKNOWN_TIME = 0L;

    private static final String NAME = "mchelden_graves";
    private static final String KEY_GRAVES = "graves";
    private static final String KEY_POS = "pos";
    private static final String KEY_OWNER = "owner";
    private static final String KEY_DIED_AT = "diedAt";

    private static final Factory<GraveRegistry> FACTORY =
            new Factory<>(GraveRegistry::new, GraveRegistry::load);

    /** Besitzer und Entstehungszeit eines Grabes. */
    private record Entry(UUID owner, long diedAt) {
    }

    /**
     * Eintragungsreihenfolge bleibt erhalten.
     *
     * <p>Sie ist der Stichentscheid beim Sortieren: zwei Graeber aus demselben Tick behalten
     * ihre Reihenfolge, damit ein Abraeumen immer gleich ablaeuft.
     */
    private final Map<BlockPos, Entry> graves = new LinkedHashMap<>();

    public static GraveRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /**
     * Traegt ein Grab ein. Eine Stelle kann nur ein Grab tragen.
     *
     * @param diedAt Spielzeit der Welt im Moment des Todes
     */
    public void add(BlockPos pos, UUID owner, long diedAt) {
        graves.put(pos.immutable(), new Entry(owner, diedAt));
        setDirty();
    }

    /** Traegt ein Grab aus. Ein zweites Mal ist harmlos. */
    public void remove(BlockPos pos) {
        if (graves.remove(pos) != null) {
            setDirty();
        }
    }

    /** Alle bekannten Graeber, neueste zuerst. */
    public List<BlockPos> all() {
        return newestFirst(graves.keySet());
    }

    /** Die Graeber eines Spielers, neueste zuerst. */
    public List<BlockPos> of(UUID owner) {
        List<BlockPos> found = new ArrayList<>();
        for (Map.Entry<BlockPos, Entry> entry : graves.entrySet()) {
            if (owner.equals(entry.getValue().owner())) {
                found.add(entry.getKey());
            }
        }
        return newestFirst(found);
    }

    /**
     * Das jüngste Grab eines Spielers.
     *
     * <p>Das eine, von dem die Respawn-Nachricht erzaehlt. Aeltere Graeber sind selbst
     * verschuldet und stehen nur noch in {@code /helden grave list}.
     */
    public Optional<BlockPos> newestOf(UUID owner) {
        List<BlockPos> found = of(owner);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /** Wem das Grab an dieser Stelle gehoert, falls dort eines eingetragen ist. */
    public Optional<UUID> ownerOf(BlockPos pos) {
        Entry entry = graves.get(pos);
        return entry != null ? Optional.of(entry.owner()) : Optional.empty();
    }

    /** Wann das Grab an dieser Stelle entstand, oder {@link #UNKNOWN_TIME}. */
    public long diedAt(BlockPos pos) {
        Entry entry = graves.get(pos);
        return entry != null ? entry.diedAt() : UNKNOWN_TIME;
    }

    public void clear() {
        graves.clear();
        setDirty();
    }

    /** Stabil absteigend nach Entstehungszeit. Bei Gleichstand gewinnt der frueher eingetragene. */
    private List<BlockPos> newestFirst(Iterable<BlockPos> positions) {
        List<BlockPos> sorted = new ArrayList<>();
        positions.forEach(sorted::add);
        sorted.sort(Comparator.comparingLong(this::diedAt).reversed());
        return sorted;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();

        for (Map.Entry<BlockPos, Entry> entry : graves.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putLong(KEY_POS, entry.getKey().asLong());
            one.putUUID(KEY_OWNER, entry.getValue().owner());
            one.putLong(KEY_DIED_AT, entry.getValue().diedAt());
            list.add(one);
        }

        tag.put(KEY_GRAVES, list);
        return tag;
    }

    /**
     * Liest das Verzeichnis zurueck.
     *
     * <p>Ein Eintrag ohne {@code diedAt} kommt aus einer Welt von vor dieser Aufzeichnung.
     * {@code getLong} liefert dann null, und das ist genau {@link #UNKNOWN_TIME} — die
     * alten Graeber brauchen also keine Wanderung.
     */
    static GraveRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        GraveRegistry registry = new GraveRegistry();
        ListTag list = tag.getList(KEY_GRAVES, Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag one = list.getCompound(i);
            registry.graves.put(BlockPos.of(one.getLong(KEY_POS)),
                    new Entry(one.getUUID(KEY_OWNER), one.getLong(KEY_DIED_AT)));
        }

        return registry;
    }
}
