package com.alejandro.synclife.model;

import java.time.LocalDate;

/** Par (rutina, día) que identifica una instancia generada. */
public record RoutineDay(long routineId, LocalDate date) {
}
