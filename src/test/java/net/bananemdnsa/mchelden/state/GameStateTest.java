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
     * Start die Border erneut auf 4000 — und zwei Stunden Schrumpfen waeren weg.
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

    /** Eine Speicherdatei darf nicht ins Leere laufen. */
    @Test
    void unbekannteGespeicherteIdFaelltAufAufbauZurueck() {
        assertEquals(Phase.AUFBAU, Phase.bySavedId("gibtsnicht"));
    }

    /**
     * Eine Tastatureingabe dagegen schon. Frueher setzte ein vertipptes `phase set kreig`
     * stillschweigend den Aufbau — mitten in der Staffel haette das die Wand hochgezogen
     * und die Border zurueckgesetzt.
     */
    @Test
    void unbekannteEingabeWirdAbgelehnt() {
        assertNull(Phase.byId("gibtsnicht"));
        assertNull(Phase.byId("kreig"));
    }

    @Test
    void phasenkettePasst() {
        assertEquals(Phase.KRIEG, Phase.AUFBAU.next());
        assertEquals(Phase.FINAL_WAR, Phase.KRIEG.next());
        assertNull(Phase.FINAL_WAR.next());
    }

    /**
     * Welten von vor der Umstellung tragen die deutschen Kennungen in ihrer Datei. Ohne das
     * Mitlesen stuenden sie nach dem Laden still wieder im Aufbau.
     */
    @Test
    void alteDeutscheIdsWerdenNochGelesen() {
        assertEquals(Phase.AUFBAU, Phase.bySavedId("aufbau"));
        assertEquals(Phase.KRIEG, Phase.bySavedId("krieg"));
    }

    /** Aber nur aus der Datei — eintippen laesst sich die alte Kennung nicht mehr. */
    @Test
    void alteDeutscheIdsSindKeineEingabeMehr() {
        assertNull(Phase.byId("aufbau"));
        assertNull(Phase.byId("krieg"));
    }

    @Test
    void dieIdsSindEnglisch() {
        assertEquals("buildup", Phase.AUFBAU.getId());
        assertEquals("war", Phase.KRIEG.getId());
        assertEquals("finalwar", Phase.FINAL_WAR.getId());
    }

    @Test
    void jedeIdLaesstSichZurueckaufloesen() {
        for (Phase phase : Phase.values()) {
            assertEquals(phase, Phase.byId(phase.getId()));
        }
    }

    /**
     * Die Arenamitte muss den Neustart ueberstehen.
     *
     * <p>Sonst stuenden nach jedem Serverstart Safezone und Trennwand wieder bei 0,0,
     * waehrend die Weltborder dort bleibt, wo sie hingeschoben wurde — die Wand laege
     * ploetzlich nicht mehr in der Mitte, und eine Seite haette mehr Land als die andere.
     */
    @Test
    void dieArenamitteUeberlebtDieSpeicherrunde() {
        GameState state = new GameState();
        state.setCenter(1500.0, -800.0);

        CompoundTag tag = state.save(new CompoundTag(), null);
        GameState geladen = GameState.load(tag, null);

        assertEquals(1500.0, geladen.getCenterX(), 1.0e-9);
        assertEquals(-800.0, geladen.getCenterZ(), 1.0e-9);
    }

    @Test
    void frischeWeltLiegtAufNullNull() {
        GameState state = new GameState();
        assertEquals(0.0, state.getCenterX(), 1.0e-9);
        assertEquals(0.0, state.getCenterZ(), 1.0e-9);
    }

    /** Eine Welt von vor dieser Aenderung hat keinen Eintrag und muss auf 0,0 landen. */
    @Test
    void alteWeltOhneEintragLiegtAufNullNull() {
        GameState geladen = GameState.load(new CompoundTag(), null);
        assertEquals(0.0, geladen.getCenterX(), 1.0e-9);
        assertEquals(0.0, geladen.getCenterZ(), 1.0e-9);
    }

    /**
     * Ein Reset laesst die Arenamitte stehen.
     *
     * <p>Dieselbe Ueberlegung wie bei der Bordergroesse, die {@code reset} ebenfalls nicht
     * anfasst: wo die Arena liegt, ist Weltaufbau und nicht der Zustand einer Runde. Wer
     * eine neue Runde startet, will nicht, dass ihm die Arena unter den Fuessen wegspringt.
     */
    @Test
    void resetLaesstDieMitteStehen() {
        GameState state = new GameState();
        state.setCenter(1500.0, -800.0);
        state.reset();

        assertEquals(1500.0, state.getCenterX(), 1.0e-9);
        assertEquals(-800.0, state.getCenterZ(), 1.0e-9);
    }

    /**
     * Ein Ein-Stunden-Event, das ein Serverneustart auffrisst, waere der aergerlichste
     * denkbare Fehler — und die Bossbar baut sich nach dem Neustart genau hieraus wieder
     * auf.
     */
    @Test
    void dasLaufendeEventUeberlebtDieSpeicherrunde() {
        GameState state = new GameState();
        state.setEvent("notimelimit", 1_000L, 61_000L);

        CompoundTag tag = state.save(new CompoundTag(), null);
        GameState geladen = GameState.load(tag, null);

        assertEquals("notimelimit", geladen.getEventId());
        assertEquals(1_000L, geladen.getEventStartedAt());
        assertEquals(61_000L, geladen.getEventEndsAt());
    }

    /** Eine Welt von vor dieser Aenderung hat keinen Eintrag und darf kein Event haben. */
    @Test
    void alteWeltOhneEintragHatKeinEvent() {
        assertEquals("", GameState.load(new CompoundTag(), null).getEventId());
    }

    @Test
    void frischerZustandHatKeinEvent() {
        assertEquals("", new GameState().getEventId());
    }

    /** Ein beendetes Event darf keine Spuren hinterlassen, sonst zeigt die Bossbar es weiter an. */
    @Test
    void clearEventLoeschtDasEvent() {
        GameState state = new GameState();
        state.setEvent("notimelimit", 1_000L, 61_000L);
        state.clearEvent();

        assertEquals("", state.getEventId());
        assertEquals(0L, state.getEventStartedAt());
        assertEquals(0L, state.getEventEndsAt());
    }

    /**
     * Ein laufendes Event ist Zustand einer Runde, nicht Weltaufbau — anders als die
     * Arenamitte, die {@code reset} deshalb stehen laesst. Wer eine neue Runde startet, soll
     * kein Event aus der vorigen mitschleppen.
     */
    @Test
    void resetBeendetEinLaufendesEvent() {
        GameState state = new GameState();
        state.setEvent("notimelimit", 1_000L, 61_000L);
        state.reset();

        assertEquals("", state.getEventId());
    }
}
