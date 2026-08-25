package net.bananemdnsa.mchelden.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.Test;

class GameStateTest {

    @Test
    void phaseUeberlebtDieSpeicherrunde() {
        GameState state = new GameState();
        state.setPhase(Phase.FINAL_WAR);

        CompoundTag tag = state.save(new CompoundTag(), null);

        assertEquals(Phase.FINAL_WAR, GameState.load(tag, null).getPhase());
    }

    @Test
    void frischerZustandStartetImAufbau() {
        assertEquals(Phase.AUFBAU, new GameState().getPhase());
    }

    @Test
    void unbekannteIdFaelltAufAufbauZurueck() {
        assertEquals(Phase.AUFBAU, Phase.byId("gibtsnicht"));
    }

    @Test
    void phasenkettePasst() {
        assertEquals(Phase.KRIEG, Phase.AUFBAU.next());
        assertEquals(Phase.FINAL_WAR, Phase.KRIEG.next());
        assertNull(Phase.FINAL_WAR.next());
    }

    @Test
    void jedeIdLaesstSichZurueckaufloesen() {
        for (Phase phase : Phase.values()) {
            assertEquals(phase, Phase.byId(phase.getId()));
        }
    }
}
