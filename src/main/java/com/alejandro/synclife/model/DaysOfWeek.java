package com.alejandro.synclife.model;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Conversión entre un conjunto de días de la semana y la máscara de bits que
 * guarda {@code Routine.daysMask}: bit0 = lunes … bit6 = domingo.
 */
public final class DaysOfWeek {

    public static final int WEEKDAYS = 0b0011111;
    public static final int WEEKEND = 0b1100000;
    public static final int ALL = 0b1111111;

    private static final String[] LETTERS = {"L", "M", "X", "J", "V", "S", "D"};

    private DaysOfWeek() {
    }

    public static int bit(DayOfWeek day) {
        return 1 << (day.getValue() - 1);
    }

    public static int toMask(Set<DayOfWeek> days) {
        int mask = 0;
        for (DayOfWeek d : days) {
            mask |= bit(d);
        }
        return mask;
    }

    public static Set<DayOfWeek> fromMask(int mask) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (DayOfWeek d : DayOfWeek.values()) {
            if (contains(mask, d)) {
                days.add(d);
            }
        }
        return days;
    }

    public static boolean contains(int mask, DayOfWeek day) {
        return (mask & bit(day)) != 0;
    }

    public static String letter(DayOfWeek day) {
        return LETTERS[day.getValue() - 1];
    }

    /** Texto corto para la UI: "Todos los días", "Laborables", "Fin de semana" o "L X V". */
    public static String format(int mask) {
        if (mask == ALL) {
            return "Todos los días";
        }
        if (mask == WEEKDAYS) {
            return "Laborables";
        }
        if (mask == WEEKEND) {
            return "Fin de semana";
        }
        StringJoiner joiner = new StringJoiner(" ");
        for (DayOfWeek d : fromMask(mask)) {
            joiner.add(letter(d));
        }
        return joiner.toString();
    }
}
