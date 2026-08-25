package net.bananemdnsa.mchelden.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.Test;

/**
 * Prueft die Persistenz-Runde: speichern, laden, gleicher Zustand.
 * Das ist genau das, was beim Serverneustart passiert.
 */
class PlayerStateStoreTest {

    private static PlayerStateStore roundTrip(PlayerStateStore store) {
        CompoundTag tag = store.save(new CompoundTag(), null);
        return PlayerStateStore.load(tag, null);
    }

    @Test
    void spielerzustandUeberlebtDieSpeicherrunde() {
        PlayerStateStore store = new PlayerStateStore();
        UUID uuid = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        PlayerState state = store.getOrCreate(uuid);
        state.setName("Morten");
        state.setHearts(2);
        state.setBountyTarget(target);
        state.setBountyResolved(true);
        state.setPlaytimeUsedSeconds(900);
        state.setPlaytimeResetDay(20_324L);
        state.setEliminated(true);

        PlayerState restored = roundTrip(store).find(uuid);

        assertNotNull(restored);
        assertEquals("Morten", restored.getName());
        assertEquals(2, restored.getHearts());
        assertEquals(target, restored.getBountyTarget());
        assertTrue(restored.isBountyResolved());
        assertEquals(900, restored.getPlaytimeUsedSeconds());
        assertEquals(20_324L, restored.getPlaytimeResetDay());
        assertTrue(restored.isEliminated());
    }

    @Test
    void neuerSpielerStartetMitDreiHerzenUndOhneBounty() {
        PlayerStateStore store = new PlayerStateStore();
        PlayerState state = store.getOrCreate(UUID.randomUUID());

        assertEquals(PlayerState.DEFAULT_HEARTS, state.getHearts());
        assertNull(state.getBountyTarget());
        assertFalse(state.isEliminated());
    }

    @Test
    void fehlendesBountyZielUeberlebtAlsNull() {
        PlayerStateStore store = new PlayerStateStore();
        UUID uuid = UUID.randomUUID();
        store.getOrCreate(uuid).setName("Felix");

        PlayerState restored = roundTrip(store).find(uuid);

        assertNotNull(restored);
        assertNull(restored.getBountyTarget());
    }

    @Test
    void mehrereSpielerBleibenGetrennt() {
        PlayerStateStore store = new PlayerStateStore();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        store.getOrCreate(first).setHearts(1);
        store.getOrCreate(second).setHearts(4);

        PlayerStateStore loaded = roundTrip(store);

        assertEquals(1, loaded.find(first).getHearts());
        assertEquals(4, loaded.find(second).getHearts());
    }

    @Test
    void countAliveZaehltNurNichtAusgeschiedene() {
        PlayerStateStore store = new PlayerStateStore();
        store.getOrCreate(UUID.randomUUID());
        store.getOrCreate(UUID.randomUUID());
        store.getOrCreate(UUID.randomUUID()).setEliminated(true);

        assertEquals(2, store.countAlive());
        assertEquals(2, roundTrip(store).countAlive());
    }

    @Test
    void unbekannterSpielerLiefertNull() {
        assertNull(new PlayerStateStore().find(UUID.randomUUID()));
    }
}
