package net.bananemdnsa.mchelden.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /**
     * Die Wand ist ein Schalter, kein abgeleiteter Wert — anders als das Zeitlimit, das die
     * Phase befragt. Grund: {@code wall drop} und {@code wall raise} sind eigenstaendige
     * Commands. Ein Schalter muss den Neustart ueberstehen, sonst stuende die Wand nach
     * jedem Serverstart wieder mitten im Krieg.
     */
    @Test
    void wandzustandUeberlebtDieSpeicherrunde() {
        GameState state = new GameState();
        state.setWallUp(false);

        CompoundTag tag = state.save(new CompoundTag(), null);

        assertFalse(GameState.load(tag, null).isWallUp());
    }

    @Test
    void frischeWeltHatEineStehendeWand() {
        assertTrue(new GameState().isWallUp());
    }

    @Test
    void frischerZustandStartetImAufbau() {
        assertEquals(Phase.AUFBAU, new GameState().getPhase());
    }

    /**
     * Der Border-Schalter ist der Grund, warum ein Neustart mitten im Final War die Arena
     * nicht zurueckwirft. Ueberlebt er die Speicherrunde nicht, setzt die Mod bei jedem
     * Start die Border erneut auf 2000 — und zwei Stunden Schrumpfen waeren weg.
     */
    @Test
    void borderSchalterUeberlebtDieSpeicherrunde() {
        GameState state = new GameState();
        state.setBorderSet(true);

        CompoundTag tag = state.save(new CompoundTag(), null);

        assertTrue(GameState.load(tag, null).isBorderSet());
    }

    /** Ohne Eintrag gilt: noch nicht gesetzt. Bestehende Welten bekommen ihre Border damit. */
    @Test
    void ohneEintragGiltDieBorderAlsUngesetzt() {
        assertFalse(GameState.load(new CompoundTag(), null).isBorderSet());
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
