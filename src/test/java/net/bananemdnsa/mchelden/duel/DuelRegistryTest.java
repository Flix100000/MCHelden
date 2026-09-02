package net.bananemdnsa.mchelden.duel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import net.bananemdnsa.mchelden.combat.HitTimer;

import org.junit.jupiter.api.Test;

/**
 * Die laufenden Duelle. Ein Duell ist immer gegenseitig — ein Zustand, in dem einer einen
 * Gegner hat und der Gegner ihn nicht, wuerde den Herzschutz stillschweigend aushebeln.
 */
class DuelRegistryTest {
    private final DuelRegistry registry = new DuelRegistry();
    private final UUID anna = UUID.randomUUID();
    private final UUID bert = UUID.randomUUID();
    private final UUID clara = UUID.randomUUID();

    @Test
    void eineFrischeListeKenntKeineDuelle() {
        assertFalse(registry.isDueling(anna));
        assertNull(registry.partnerOf(anna));
        assertEquals(0, registry.remainingTicks(anna));
    }

    @Test
    void dasDuellStehtAufBeidenSeiten() {
        registry.open(anna, bert);
        assertTrue(registry.isDueling(anna));
        assertTrue(registry.isDueling(bert));
        assertEquals(bert, registry.partnerOf(anna));
        assertEquals(anna, registry.partnerOf(bert));
        assertTrue(registry.arePartners(anna, bert));
        assertTrue(registry.arePartners(bert, anna));
        assertFalse(registry.arePartners(anna, clara));
    }

    /** Die Annahme selbst nimmt die Rolle des ersten Treffers ein: 30 Sekunden, sofort. */
    @Test
    void dasDuellStartetBeiDreissigSekunden() {
        registry.open(anna, bert);
        assertEquals(30 * 20, registry.remainingTicks(anna));
        assertEquals(30 * 20, registry.remainingTicks(bert));
    }

    /** Ein geteilter Timer: was der eine schlaegt, sieht der andere im selben Balken. */
    @Test
    void beideTeilenSichDenselbenTimer() {
        registry.open(anna, bert);
        for (int hit = 0; hit < 4; hit++) {
            registry.hit(anna);
        }
        assertEquals(60 * 20, registry.remainingTicks(anna));
        assertEquals(60 * 20, registry.remainingTicks(bert));
    }

    @Test
    void schliessenGibtBeideFreiUndMeldetDasPaar() {
        registry.open(anna, bert);
        DuelRegistry.Duel closed = registry.close(anna);
        assertEquals(bert, closed.partnerOf(anna));
        assertFalse(registry.isDueling(anna));
        assertFalse(registry.isDueling(bert));
        assertNull(registry.close(anna));
    }

    @Test
    void derTimerLaeuftAbUndMeldetDasDuell() {
        registry.open(anna, bert);

        for (int tick = 1; tick < 30 * 20; tick++) {
            assertTrue(registry.tick().isEmpty(), "Tick " + tick + " haette nichts beenden duerfen");
        }

        List<DuelRegistry.Duel> expired = registry.tick();
        assertEquals(1, expired.size());
        assertEquals(bert, expired.get(0).partnerOf(anna));
        assertFalse(registry.isDueling(anna));
        assertFalse(registry.isDueling(bert));
    }

    /** Der Timer wird beim Platzen weitergereicht, nicht neu gestartet. */
    @Test
    void derTimerUeberlebtDasSchliessen() {
        registry.open(anna, bert);
        registry.hit(anna);
        registry.hit(anna);

        int remaining = registry.remainingTicks(anna);
        DuelRegistry.Duel closed = registry.close(bert);

        HitTimer carried = new HitTimer();
        carried.adopt(closed.timer());
        assertEquals(remaining, carried.remainingTicks());
    }

    /** Zwei Duelle nebeneinander duerfen sich nicht ins Gehege kommen. */
    @Test
    void zweiDuelleLaufenUnabhaengig() {
        UUID dora = UUID.randomUUID();
        registry.open(anna, bert);
        registry.open(clara, dora);

        registry.hit(anna);
        registry.hit(anna);
        registry.hit(anna);
        registry.hit(anna);

        assertEquals(60 * 20, registry.remainingTicks(bert));
        assertEquals(30 * 20, registry.remainingTicks(dora));
    }
}
