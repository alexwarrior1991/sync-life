package com.alejandro.synclife.ui;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class FormatsTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);

    @Test
    void dayTitleInSpanish() {
        assertThat(Formats.dayTitle(MONDAY)).isEqualTo("Lunes, 12 de octubre");
        assertThat(Formats.dayTitle(LocalDate.of(2026, 10, 10))).isEqualTo("Sábado, 10 de octubre");
    }

    @Test
    void shortAndRelativeDays() {
        assertThat(Formats.dayShort(MONDAY.plusDays(2))).isEqualTo("Mié 14");
        assertThat(Formats.relativeDay(MONDAY, MONDAY)).isEqualTo("Hoy");
        assertThat(Formats.relativeDay(MONDAY.plusDays(1), MONDAY)).isEqualTo("Mañana");
        assertThat(Formats.relativeDay(MONDAY.minusDays(1), MONDAY)).isEqualTo("Ayer");
        assertThat(Formats.relativeDay(MONDAY.plusDays(5), MONDAY)).isEqualTo("Sáb 17 oct");
        assertThat(Formats.relativeDay(LocalDate.of(2027, 1, 4), MONDAY)).isEqualTo("Lun 4 ene 2027");
        assertThat(Formats.relativeDay(null, MONDAY)).isEmpty();
    }

    @Test
    void durations() {
        assertThat(Formats.duration(null)).isEmpty();
        assertThat(Formats.duration(0)).isEmpty();
        assertThat(Formats.duration(45)).isEqualTo("45 min");
        assertThat(Formats.duration(120)).isEqualTo("2 h");
        assertThat(Formats.duration(90)).isEqualTo("1 h 30 min");
    }

    @Test
    void countdownRoundsUpToTheNextSecond() {
        assertThat(Formats.countdown(Duration.ofMinutes(25))).isEqualTo("25:00");
        assertThat(Formats.countdown(Duration.ofMillis(61_200))).isEqualTo("01:02");
        assertThat(Formats.countdown(Duration.ofMinutes(90))).isEqualTo("1:30:00");
        assertThat(Formats.countdown(Duration.ZERO)).isEqualTo("00:00");
        assertThat(Formats.countdown(Duration.ofSeconds(-5))).isEqualTo("00:00");
    }

    @Test
    void trackedAgainstEstimate() {
        assertThat(Formats.trackedVsEstimate(50, 60)).isEqualTo("50 min de 1 h (83 %)");
        assertThat(Formats.trackedVsEstimate(90, null)).isEqualTo("1 h 30 min");
        assertThat(Formats.trackedVsEstimate(0, 30)).isEqualTo("0 min de 30 min (0 %)");
    }

    @Test
    void weekTitles() {
        assertThat(Formats.weekTitle(MONDAY)).isEqualTo("Semana 42 · 12–18 oct");
        assertThat(Formats.weekTitle(LocalDate.of(2026, 9, 28))).isEqualTo("Semana 40 · 28 sept – 4 oct");
        assertThat(Formats.mondayOf(LocalDate.of(2026, 10, 18))).isEqualTo(MONDAY);
    }

    @Test
    void lenientTimeParsing() {
        assertThat(Formats.parseTime("9")).contains(LocalTime.of(9, 0));
        assertThat(Formats.parseTime("930")).contains(LocalTime.of(9, 30));
        assertThat(Formats.parseTime("9:30")).contains(LocalTime.of(9, 30));
        assertThat(Formats.parseTime(" 19:05 ")).contains(LocalTime.of(19, 5));
        assertThat(Formats.parseTime("24:00")).isEmpty();
        assertThat(Formats.parseTime("12:7")).isEmpty();
        assertThat(Formats.parseTime("")).isEmpty();
        assertThat(Formats.time(LocalTime.of(7, 5))).isEqualTo("07:05");
    }
}
