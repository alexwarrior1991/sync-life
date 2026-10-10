package com.alejandro.synclife.model;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class DaysOfWeekTest {

    @Test
    void mondayIsBitZeroAndSundayBitSix() {
        assertThat(DaysOfWeek.bit(DayOfWeek.MONDAY)).isEqualTo(1);
        assertThat(DaysOfWeek.bit(DayOfWeek.SUNDAY)).isEqualTo(64);
    }

    @Test
    void roundTripsBetweenSetAndMask() {
        EnumSet<DayOfWeek> lxv = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
        int mask = DaysOfWeek.toMask(lxv);
        assertThat(mask).isEqualTo(0b0010101);
        assertThat(DaysOfWeek.fromMask(mask)).isEqualTo(lxv);
        assertThat(DaysOfWeek.contains(mask, DayOfWeek.TUESDAY)).isFalse();
    }

    @Test
    void presetsCoverTheWholeWeek() {
        assertThat(DaysOfWeek.WEEKDAYS | DaysOfWeek.WEEKEND).isEqualTo(DaysOfWeek.ALL);
        assertThat(DaysOfWeek.fromMask(DaysOfWeek.WEEKEND))
                .containsExactly(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
    }

    @Test
    void formatsInSpanish() {
        assertThat(DaysOfWeek.format(0b0010101)).isEqualTo("L X V");
        assertThat(DaysOfWeek.format(DaysOfWeek.WEEKDAYS)).isEqualTo("Laborables");
        assertThat(DaysOfWeek.format(DaysOfWeek.WEEKEND)).isEqualTo("Fin de semana");
        assertThat(DaysOfWeek.format(DaysOfWeek.ALL)).isEqualTo("Todos los días");
    }
}
