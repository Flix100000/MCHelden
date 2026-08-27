package net.bananemdnsa.mchelden.grave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.Test;

/**
 * Das Verzeichnis ist der einzige Weg, ein Grab in einem ungeladenen Chunk wiederzufinden.
 * Ueberlebt es den Neustart nicht, bleiben nach einem `reset graves` Steine in der Welt
 * stehen, die niemand mehr zuordnen kann.
 */
class GraveRegistryTest {

    private static final UUID ANNA = UUID.fromString("00000000-0000-0000-0000-00000000a11a");
    private static final UUID BEN = UUID.fromString("00000000-0000-0000-0000-00000000be47");

    @Test
    void einGrabLaesstSichWiederfinden() {
        GraveRegistry registry = new GraveRegistry();
        registry.add(new BlockPos(10, 64, -20), ANNA);

        assertEquals(List.of(new BlockPos(10, 64, -20)), registry.all());
    }

    @Test
    void nachBesitzerGefiltert() {
        GraveRegistry registry = new GraveRegistry();
        registry.add(new BlockPos(1, 64, 1), ANNA);
        registry.add(new BlockPos(2, 64, 2), BEN);
        registry.add(new BlockPos(3, 64, 3), ANNA);

        assertEquals(2, registry.of(ANNA).size());
        assertEquals(List.of(new BlockPos(2, 64, 2)), registry.of(BEN));
    }

    @Test
    void ausgetrageneGraeberSindWeg() {
        GraveRegistry registry = new GraveRegistry();
        registry.add(new BlockPos(1, 64, 1), ANNA);
        registry.remove(new BlockPos(1, 64, 1));

        assertTrue(registry.all().isEmpty());
    }

    /** Ein zweites Austragen derselben Stelle darf nicht stoeren. */
    @Test
    void zweimalAustragenIstHarmlos() {
        GraveRegistry registry = new GraveRegistry();
        registry.add(new BlockPos(1, 64, 1), ANNA);
        registry.remove(new BlockPos(1, 64, 1));
        registry.remove(new BlockPos(1, 64, 1));

        assertTrue(registry.all().isEmpty());
    }

    /** Zwei Graeber an derselben Stelle kann es nicht geben — das zweite ersetzt das erste. */
    @Test
    void dieselbeStelleZaehltEinmal() {
        GraveRegistry registry = new GraveRegistry();
        registry.add(new BlockPos(1, 64, 1), ANNA);
        registry.add(new BlockPos(1, 64, 1), BEN);

        assertEquals(1, registry.all().size());
        assertEquals(List.of(new BlockPos(1, 64, 1)), registry.of(BEN));
        assertTrue(registry.of(ANNA).isEmpty());
    }

    @Test
    void ueberlebtDieSpeicherrunde() {
        GraveRegistry registry = new GraveRegistry();
        registry.add(new BlockPos(10, 64, -20), ANNA);
        registry.add(new BlockPos(-5, 30, 7), BEN);

        CompoundTag tag = registry.save(new CompoundTag(), null);
        GraveRegistry loaded = GraveRegistry.load(tag, null);

        assertEquals(2, loaded.all().size());
        assertEquals(List.of(new BlockPos(10, 64, -20)), loaded.of(ANNA));
        assertEquals(List.of(new BlockPos(-5, 30, 7)), loaded.of(BEN));
    }

    @Test
    void leerenRaeumtAllesWeg() {
        GraveRegistry registry = new GraveRegistry();
        registry.add(new BlockPos(1, 64, 1), ANNA);
        registry.add(new BlockPos(2, 64, 2), BEN);
        registry.clear();

        assertTrue(registry.all().isEmpty());
        assertFalse(registry.all().contains(new BlockPos(1, 64, 1)));
    }
}
