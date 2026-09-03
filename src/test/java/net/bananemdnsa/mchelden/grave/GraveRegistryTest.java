package net.bananemdnsa.mchelden.grave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
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

    /** Irgendein Zeitpunkt. Wo er nicht zur Sache gehoert, ist er ueberall derselbe. */
    private static final long SOMETIME = 1_000L;

    @Test
    void einGrabLaesstSichWiederfinden() {
        GraveRegistry registry = new GraveRegistry();
        registry.add(new BlockPos(10, 64, -20), ANNA, SOMETIME);

        assertEquals(List.of(new BlockPos(10, 64, -20)), registry.all());
    }

    @Test
    void nachBesitzerGefiltert() {
        GraveRegistry registry = new GraveRegistry();
        registry.add(new BlockPos(1, 64, 1), ANNA, SOMETIME);
        registry.add(new BlockPos(2, 64, 2), BEN, SOMETIME);
        registry.add(new BlockPos(3, 64, 3), ANNA, SOMETIME);

        assertEquals(2, registry.of(ANNA).size());
        assertEquals(List.of(new BlockPos(2, 64, 2)), registry.of(BEN));
    }

    @Test
    void ausgetrageneGraeberSindWeg() {
        GraveRegistry registry = new GraveRegistry();
        registry.add(new BlockPos(1, 64, 1), ANNA, SOMETIME);
        registry.remove(new BlockPos(1, 64, 1));

        assertTrue(registry.all().isEmpty());
    }

    /** Ein zweites Austragen derselben Stelle darf nicht stoeren. */
    @Test
    void zweimalAustragenIstHarmlos() {
        GraveRegistry registry = new GraveRegistry();
        registry.add(new BlockPos(1, 64, 1), ANNA, SOMETIME);
        registry.remove(new BlockPos(1, 64, 1));
        registry.remove(new BlockPos(1, 64, 1));

        assertTrue(registry.all().isEmpty());
    }

    /** Zwei Graeber an derselben Stelle kann es nicht geben — das zweite ersetzt das erste. */
    @Test
    void dieselbeStelleZaehltEinmal() {
        GraveRegistry registry = new GraveRegistry();
        registry.add(new BlockPos(1, 64, 1), ANNA, SOMETIME);
        registry.add(new BlockPos(1, 64, 1), BEN, SOMETIME);

        assertEquals(1, registry.all().size());
        assertEquals(List.of(new BlockPos(1, 64, 1)), registry.of(BEN));
        assertTrue(registry.of(ANNA).isEmpty());
    }

    @Test
    void ueberlebtDieSpeicherrunde() {
        GraveRegistry registry = new GraveRegistry();
        registry.add(new BlockPos(10, 64, -20), ANNA, SOMETIME);
        registry.add(new BlockPos(-5, 30, 7), BEN, SOMETIME);

        CompoundTag tag = registry.save(new CompoundTag(), null);
        GraveRegistry loaded = GraveRegistry.load(tag, null);

        assertEquals(2, loaded.all().size());
        assertEquals(List.of(new BlockPos(10, 64, -20)), loaded.of(ANNA));
        assertEquals(List.of(new BlockPos(-5, 30, 7)), loaded.of(BEN));
    }

    @Test
    void leerenRaeumtAllesWeg() {
        GraveRegistry registry = new GraveRegistry();
        registry.add(new BlockPos(1, 64, 1), ANNA, SOMETIME);
        registry.add(new BlockPos(2, 64, 2), BEN, SOMETIME);
        registry.clear();

        assertTrue(registry.all().isEmpty());
        assertFalse(registry.all().contains(new BlockPos(1, 64, 1)));
    }

    /** Ohne ihn koennte die Respawn-Nachricht das neueste Grab nicht bestimmen. */
    @Test
    void derZeitstempelUeberlebtDieSpeicherrunde() {
        GraveRegistry registry = new GraveRegistry();
        registry.add(new BlockPos(1, 64, 1), ANNA, 4711L);

        CompoundTag tag = registry.save(new CompoundTag(), null);
        GraveRegistry loaded = GraveRegistry.load(tag, null);

        assertEquals(4711L, loaded.diedAt(new BlockPos(1, 64, 1)));
    }

    /** Eine Stelle ohne Eintrag hat keinen Zeitpunkt. */
    @Test
    void eineLeereStelleHatKeinenZeitstempel() {
        GraveRegistry registry = new GraveRegistry();

        assertEquals(GraveRegistry.UNKNOWN_TIME, registry.diedAt(new BlockPos(1, 64, 1)));
        assertTrue(registry.ownerOf(new BlockPos(1, 64, 1)).isEmpty());
    }

    /**
     * Nach Zeit, nicht nach Eintragungsreihenfolge.
     *
     * <p>Beim gewoehnlichen Spielverlauf ist beides dasselbe. Beim Combat-Logout nicht: der
     * Respawn wird nachgeholt, und das Grab kann in beliebiger Reihenfolge dazukommen.
     */
    @Test
    void dasNeuesteGrabGehtNachDerZeit() {
        GraveRegistry registry = new GraveRegistry();
        registry.add(new BlockPos(1, 64, 1), ANNA, 300L);
        registry.add(new BlockPos(2, 64, 2), ANNA, 900L);
        registry.add(new BlockPos(3, 64, 3), ANNA, 600L);

        assertEquals(Optional.of(new BlockPos(2, 64, 2)), registry.newestOf(ANNA));
        assertEquals(
                List.of(new BlockPos(2, 64, 2), new BlockPos(3, 64, 3), new BlockPos(1, 64, 1)),
                registry.of(ANNA));
    }

    @Test
    void dasNeuesteGrabBleibtBeimBesitzer() {
        GraveRegistry registry = new GraveRegistry();
        registry.add(new BlockPos(1, 64, 1), ANNA, 300L);
        registry.add(new BlockPos(2, 64, 2), BEN, 900L);

        assertEquals(Optional.of(new BlockPos(1, 64, 1)), registry.newestOf(ANNA));
    }

    /** Wer nie gestorben ist, hat kein Grab — und bekommt keine Respawn-Nachricht. */
    @Test
    void ohneGrabKeinNeuestes() {
        GraveRegistry registry = new GraveRegistry();
        registry.add(new BlockPos(1, 64, 1), ANNA, SOMETIME);

        assertTrue(registry.newestOf(BEN).isEmpty());
    }

    /**
     * Ein Grab aus einer Welt von vor dem Zeitstempel laedt mit {@code UNKNOWN_TIME} und
     * landet damit hinten. Kaputtgehen darf dabei nichts.
     */
    @Test
    void alteEintraegeOhneZeitstempelLandenHinten() {
        CompoundTag alt = new CompoundTag();
        alt.putLong("pos", new BlockPos(9, 64, 9).asLong());
        alt.putUUID("owner", ANNA);

        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        list.add(alt);
        CompoundTag tag = new CompoundTag();
        tag.put("graves", list);

        GraveRegistry registry = GraveRegistry.load(tag, null);
        registry.add(new BlockPos(1, 64, 1), ANNA, SOMETIME);

        assertEquals(GraveRegistry.UNKNOWN_TIME, registry.diedAt(new BlockPos(9, 64, 9)));
        assertEquals(List.of(new BlockPos(1, 64, 1), new BlockPos(9, 64, 9)), registry.of(ANNA));
    }

    /** Bei Gleichstand entscheidet die Eintragungsreihenfolge, damit ein Abraeumen gleich ablaeuft. */
    @Test
    void gleicherZeitpunktBehaeltDieReihenfolge() {
        GraveRegistry registry = new GraveRegistry();
        registry.add(new BlockPos(1, 64, 1), ANNA, SOMETIME);
        registry.add(new BlockPos(2, 64, 2), ANNA, SOMETIME);
        registry.add(new BlockPos(3, 64, 3), ANNA, SOMETIME);

        assertEquals(
                List.of(new BlockPos(1, 64, 1), new BlockPos(2, 64, 2), new BlockPos(3, 64, 3)),
                registry.of(ANNA));
    }
}
