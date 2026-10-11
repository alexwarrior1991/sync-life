package com.alejandro.synclife.ui;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Opciones de recordatorio de los formularios: "Sin recordatorio", "A la hora", "N minutos antes"…
 * El valor es la antelación en minutos; {@code null} = sin recordatorio y {@link #CUSTOM} = a una hora concreta.
 */
public final class ReminderOptions {

    public static final int CUSTOM = -1;
    private static final int[] BEFORE = {0, 5, 10, 15, 30, 60, 120, 24 * 60};

    private ReminderOptions() {
    }

    /** Opciones para una tarea (con "a una hora concreta"). */
    public static List<Choice<Integer>> forTasks() {
        List<Choice<Integer>> options = relative("Sin recordatorio");
        options.add(Choice.of("A una hora concreta…", CUSTOM));
        return options;
    }

    /** Opciones para una rutina (siempre relativas a la hora de cada día, como mucho un día antes). */
    public static List<Choice<Integer>> forRoutines() {
        return relative("Sin aviso");
    }

    private static List<Choice<Integer>> relative(String none) {
        List<Choice<Integer>> options = new ArrayList<>();
        options.add(Choice.of(none, null));
        for (int minutes : BEFORE) {
            options.add(Choice.of(label(minutes), minutes));
        }
        return options;
    }

    /** "A la hora", "15 min antes", "1 h antes", "1 día antes". */
    public static String label(int minutesBefore) {
        if (minutesBefore == 0) {
            return "A la hora";
        }
        if (minutesBefore == 24 * 60) {
            return "1 día antes";
        }
        return Formats.duration(minutesBefore) + " antes";
    }

    /** Antelación que corresponde a {@code remindAt} respecto a {@code base}, si es una de las opciones; si no, CUSTOM. */
    public static Integer optionFor(LocalDateTime remindAt, LocalDateTime base) {
        if (remindAt == null) {
            return null;
        }
        if (base != null) {
            long before = ChronoUnit.MINUTES.between(remindAt, base);
            for (int minutes : BEFORE) {
                if (before == minutes) {
                    return minutes;
                }
            }
        }
        return CUSTOM;
    }

    /** Hora absoluta del aviso para la opción elegida (null si no hay recordatorio o falta el día). */
    public static LocalDateTime remindAt(Integer option, LocalDate day, LocalTime startTime, LocalTime customTime,
                                         LocalTime defaultTime) {
        if (option == null || day == null) {
            return null;
        }
        if (option == CUSTOM) {
            return customTime == null ? null : day.atTime(customTime);
        }
        return day.atTime(startTime == null ? defaultTime : startTime).minusMinutes(option);
    }
}
