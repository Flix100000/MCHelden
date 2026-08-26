package net.bananemdnsa.mchelden.playtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.LocalDate;
import java.time.LocalDateTime;

import net.bananemdnsa.mchelden.state.PlayerState;

import org.junit.jupiter.api.Test;

/**
 * Die Tagesgrenze liegt um 4 Uhr, nicht um Mitternacht — sonst spielt jemand um 23:30
 * seine halbe Stunde und bekommt um 00:00 eine volle neue.
 *
 * <p>Eine Rechnung, bei der man sich leicht um eine Stunde vertut und es erst drei Wochen
 * spaeter merkt, wenn sich jemand ueber verschenkte Zeit beschwert. Deswegen hier.
 */
class PlaytimeTrackerTest {

    private static long day(int year, int month, int dayOfMonth) {
        return LocalDate.of(year, month, dayOfMonth).toEpochDay();
    }

    private static long playDay(int year, int month, int dayOfMonth, int hour, int minute) {
        return PlaytimeTracker.playDay(LocalDateTime.of(year, month, dayOfMonth, hour, minute));
    }

    @Test
    void kurzVorVierGehoertNochZumVortag() {
        assertEquals(day(2026, 8, 25), playDay(2026, 8, 26, 3, 59));
    }

    @Test
    void punktVierBeginntDerNeueSpieltag() {
        assertEquals(day(2026, 8, 26), playDay(2026, 8, 26, 4, 0));
    }

    @Test
    void mitternachtGehoertNochZumVortag() {
        assertEquals(day(2026, 8, 25), playDay(2026, 8, 26, 0, 0));
    }

    @Test
    void abendGehoertZumSelbenTag() {
        assertEquals(day(2026, 8, 26), playDay(2026, 8, 26, 23, 59));
    }

    /** Wer um 23:30 spielt und um 00:30 weiterspielt, ist im selben Kontingent. */
    @Test
    void ueberMitternachtBleibtEsDerselbeSpieltag() {
        assertEquals(playDay(2026, 8, 26, 23, 30), playDay(2026, 8, 27, 0, 30));
    }

    @Test
    void nachVierUhrIstEsEinNeuerSpieltag() {
        assertNotEquals(playDay(2026, 8, 27, 3, 30), playDay(2026, 8, 27, 4, 30));
    }

    @Test
    void aufeinanderfolgendeSpieltageLiegenGenauEinsAuseinander() {
        assertEquals(1, playDay(2026, 8, 27, 12, 0) - playDay(2026, 8, 26, 12, 0));
    }

    @Test
    void monatswechselWirdRichtigZurueckgerechnet() {
        assertEquals(day(2026, 2, 28), playDay(2026, 3, 1, 2, 0));
    }

    @Test
    void jahreswechselWirdRichtigZurueckgerechnet() {
        assertEquals(day(2025, 12, 31), playDay(2026, 1, 1, 1, 0));
    }

    @Test
    void frischerSpielerHatDasVolleKontingent() {
        assertEquals(PlayerState.DAILY_PLAYTIME_SECONDS, PlaytimeTracker.remainingSeconds(0));
    }

    @Test
    void verbrauchteZeitWirdAbgezogen() {
        assertEquals(PlayerState.DAILY_PLAYTIME_SECONDS - 900, PlaytimeTracker.remainingSeconds(900));
    }

    /** Ueberzug aus einem Kampf kann die verbrauchte Zeit ueber das Kontingent treiben. */
    @Test
    void ueberzugFaelltNichtUnterNull() {
        assertEquals(0, PlaytimeTracker.remainingSeconds(PlayerState.DAILY_PLAYTIME_SECONDS + 180));
    }
}
