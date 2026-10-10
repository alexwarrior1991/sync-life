package com.alejandro.synclife.service;

import com.alejandro.synclife.model.DaysOfWeek;
import com.alejandro.synclife.model.entity.Routine;
import com.alejandro.synclife.model.enums.Area;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RoutineSchedulerTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);
    private static final int LXV = 0b0010101;

    @Test
    void mondayWednesdayFridayOverTwoWeeks() {
        Routine gym = new Routine("Gimnasio", Area.DEPORTE, LXV, MONDAY);
        assertThat(RoutineScheduler.occurrences(gym, MONDAY, MONDAY.plusDays(13)))
                .containsExactly(MONDAY, MONDAY.plusDays(2), MONDAY.plusDays(4),
                        MONDAY.plusDays(7), MONDAY.plusDays(9), MONDAY.plusDays(11));
    }

    @Test
    void weekdaysSkipWeekend() {
        Routine mail = new Routine("Correo", Area.TRABAJO, DaysOfWeek.WEEKDAYS, MONDAY);
        assertThat(RoutineScheduler.occurrences(mail, MONDAY, MONDAY.plusDays(6))).hasSize(5);
    }

    @Test
    void respectsStartAndEndDates() {
        Routine routine = new Routine("Curso", Area.TRABAJO, DaysOfWeek.ALL, MONDAY.plusDays(2));
        routine.setEndDate(MONDAY.plusDays(4));
        assertThat(RoutineScheduler.occurrences(routine, MONDAY, MONDAY.plusDays(10)))
                .containsExactly(MONDAY.plusDays(2), MONDAY.plusDays(3), MONDAY.plusDays(4));
    }

    @Test
    void inactiveRoutineHasNoOccurrencesButPreviewStillWorks() {
        Routine routine = new Routine("Pausada", Area.VIDA, DaysOfWeek.ALL, MONDAY);
        routine.setActive(false);
        assertThat(RoutineScheduler.occurrences(routine, MONDAY, MONDAY.plusDays(6))).isEmpty();
        assertThat(RoutineScheduler.nextOccurrences(routine, MONDAY, 3))
                .containsExactly(MONDAY, MONDAY.plusDays(1), MONDAY.plusDays(2));
    }

    @Test
    void previewStartsAtStartDateWhenInTheFuture() {
        Routine routine = new Routine("Futura", Area.VIDA, LXV, MONDAY.plusDays(7));
        assertThat(RoutineScheduler.nextOccurrences(routine, MONDAY, 2))
                .containsExactly(MONDAY.plusDays(7), MONDAY.plusDays(9));
    }
}
