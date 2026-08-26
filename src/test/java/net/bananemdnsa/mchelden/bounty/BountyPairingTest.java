package net.bananemdnsa.mchelden.bounty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.util.RandomSource;

import org.junit.jupiter.api.Test;

/**
 * Die Paarung ist die eine Stelle in Etappe 5, an der wirklich etwas schiefgehen kann:
 * jemand mit sich selbst gepaart, jemand doppelt vergeben, jemand verliert seinen Partner.
 * Der Roll passiert im Projekt genau einmal — ausprobieren im Spiel waere kein Nachweis.
 */
class BountyPairingTest {

    private static RandomSource fixedRandom() {
        return RandomSource.create(1234L);
    }

    private static List<UUID> candidates(int count) {
        List<UUID> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(UUID.nameUUIDFromBytes(("spieler" + index).getBytes()));
        }
        return result;
    }

    private static List<UUID> flatten(List<BountyPairing.Pair> pairs) {
        List<UUID> result = new ArrayList<>();
        for (BountyPairing.Pair pair : pairs) {
            result.add(pair.first());
            result.add(pair.second());
        }
        return result;
    }

    @Test
    void geradeAnzahlPaartAlleVollstaendig() {
        List<UUID> players = candidates(20);

        List<UUID> paired = flatten(BountyPairing.pair(players, fixedRandom()));

        assertEquals(20, paired.size());
        assertTrue(paired.containsAll(players));
    }

    @Test
    void ungeradeAnzahlLaesstGenauEinenUebrig() {
        List<UUID> players = candidates(21);

        List<UUID> paired = flatten(BountyPairing.pair(players, fixedRandom()));

        assertEquals(20, paired.size());
        assertTrue(players.containsAll(paired));
    }

    @Test
    void niemandKommtDoppeltVor() {
        List<UUID> paired = flatten(BountyPairing.pair(candidates(20), fixedRandom()));

        Set<UUID> distinct = new HashSet<>(paired);
        assertEquals(paired.size(), distinct.size());
    }

    @Test
    void niemandWirdMitSichSelbstGepaart() {
        List<BountyPairing.Pair> pairs = BountyPairing.pair(candidates(20), fixedRandom());

        for (BountyPairing.Pair pair : pairs) {
            assertFalse(pair.first().equals(pair.second()));
        }
    }

    @Test
    void einzelnerSpielerGehtLeerAus() {
        assertTrue(BountyPairing.pair(candidates(1), fixedRandom()).isEmpty());
    }

    @Test
    void leereListeErgibtKeinePaare() {
        assertTrue(BountyPairing.pair(List.of(), fixedRandom()).isEmpty());
    }

    @Test
    void gleicherStartwertErgibtGleichePaarung() {
        List<UUID> players = candidates(20);

        assertEquals(BountyPairing.pair(players, fixedRandom()),
                BountyPairing.pair(players, fixedRandom()));
    }

    /** Die Reihenfolge der Aufrufer darf nicht das Ergebnis sein — sonst ist nichts zufaellig. */
    @Test
    void paarungFolgtNichtDerEingabereihenfolge() {
        List<UUID> players = candidates(20);

        List<BountyPairing.Pair> pairs = BountyPairing.pair(players, fixedRandom());

        boolean anyShuffled = false;
        for (BountyPairing.Pair pair : pairs) {
            int first = players.indexOf(pair.first());
            int second = players.indexOf(pair.second());
            if (Math.abs(first - second) != 1 || Math.min(first, second) % 2 != 0) {
                anyShuffled = true;
                break;
            }
        }
        assertTrue(anyShuffled);
    }

    /** Die uebergebene Liste gehoert dem Aufrufer und darf nicht umsortiert werden. */
    @Test
    void eingabelisteBleibtUnveraendert() {
        List<UUID> players = candidates(20);
        List<UUID> before = List.copyOf(players);

        BountyPairing.pair(players, fixedRandom());

        assertEquals(before, players);
    }
}
