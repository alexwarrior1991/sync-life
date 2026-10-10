package com.alejandro.synclife.service;

import com.alejandro.synclife.model.entity.Routine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Cálculo puro (sin base de datos) de los días en que toca una rutina. */
public final class RoutineScheduler {

    private static final int MAX_LOOKAHEAD_DAYS = 366;

    private RoutineScheduler() {
    }

    /** Días entre {@code from} y {@code to} (ambos incluidos) en que la rutina activa genera tarea. */
    public static List<LocalDate> occurrences(Routine routine, LocalDate from, LocalDate to) {
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (routine.appliesOn(d)) {
                days.add(d);
            }
        }
        return days;
    }

    /** Las próximas {@code count} fechas desde {@code from}, aunque la rutina esté en pausa (para la vista previa). */
    public static List<LocalDate> nextOccurrences(Routine routine, LocalDate from, int count) {
        List<LocalDate> days = new ArrayList<>();
        if (routine.getStartDate() != null && from.isBefore(routine.getStartDate())) {
            from = routine.getStartDate();
        }
        for (int i = 0; i < MAX_LOOKAHEAD_DAYS && days.size() < count; i++) {
            LocalDate d = from.plusDays(i);
            if (routine.isScheduledOn(d)) {
                days.add(d);
            }
        }
        return days;
    }
}
