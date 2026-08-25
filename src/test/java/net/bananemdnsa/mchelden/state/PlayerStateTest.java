package net.bananemdnsa.mchelden.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Der Deckel bei vier Herzen sitzt im Setter, damit keine spaetere Etappe und kein
 * vertippter Command ein fuenftes Herz vergeben kann.
 */
class PlayerStateTest {

    private static PlayerState state() {
        return new PlayerState(UUID.randomUUID());
    }

    @Test
    void herzenSindBeiVierGedeckelt() {
        PlayerState state = state();
        state.setHearts(99);
        assertEquals(PlayerState.MAX_HEARTS, state.getHearts());
    }

    @Test
    void herzenFallenNichtUnterNull() {
        PlayerState state = state();
        state.setHearts(-5);
        assertEquals(0, state.getHearts());
    }

    @Test
    void werteZwischenNullUndVierBleibenUnveraendert() {
        PlayerState state = state();
        for (int hearts = 0; hearts <= PlayerState.MAX_HEARTS; hearts++) {
            state.setHearts(hearts);
            assertEquals(hearts, state.getHearts());
        }
    }

    @Test
    void spielzeitFaelltNichtUnterNull() {
        PlayerState state = state();
        state.setPlaytimeUsedSeconds(-30);
        assertEquals(0, state.getPlaytimeUsedSeconds());
    }
}
