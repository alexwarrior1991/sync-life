package com.alejandro.synclife.ui;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Formatos de fecha, hora y duración en español para la interfaz. */
public final class Formats {

    public static final Locale ES = Locale.of("es", "ES");

    private static final String[] DAY_SHORT = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};
    private static final String[] MONTH_SHORT =
            {"ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic"};

    private static final DateTimeFormatter DAY_TITLE = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", ES);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern TIME_WITH_COLON = Pattern.compile("^(\\d{1,2}):(\\d{2})$");
    private static final Pattern TIME_DIGITS = Pattern.compile("^(\\d{1,2})(\\d{2})?$");

    private Formats() {
    }

    /** "Lunes, 12 de octubre". */
    public static String dayTitle(LocalDate date) {
        return capitalize(DAY_TITLE.format(date));
    }

    /** "Lun 12". */
    public static String dayShort(LocalDate date) {
        return DAY_SHORT[date.getDayOfWeek().getValue() - 1] + " " + date.getDayOfMonth();
    }

    /** "12 oct". */
    public static String dayMonth(LocalDate date) {
        return date.getDayOfMonth() + " " + MONTH_SHORT[date.getMonthValue() - 1];
    }

    /** "Hoy", "Mañana", "Ayer" o "Lun 12 oct". */
    public static String relativeDay(LocalDate date, LocalDate today) {
        if (date == null) {
            return "";
        }
        if (date.equals(today)) {
            return "Hoy";
        }
        if (date.equals(today.plusDays(1))) {
            return "Mañana";
        }
        if (date.equals(today.minusDays(1))) {
            return "Ayer";
        }
        String text = dayShort(date) + " " + MONTH_SHORT[date.getMonthValue() - 1];
        return date.getYear() == today.getYear() ? text : text + " " + date.getYear();
    }

    public static String date(LocalDate date) {
        return date == null ? "" : DATE.format(date);
    }

    public static String time(LocalTime time) {
        return time == null ? "" : TIME.format(time);
    }

    /** Cuenta atrás "mm:ss" (o "h:mm:ss" a partir de una hora), redondeando los segundos hacia arriba. */
    public static String countdown(java.time.Duration remaining) {
        long seconds = Math.max(0, (remaining.toMillis() + 999) / 1000);
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        long rest = seconds % 60;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, rest)
                : String.format("%02d:%02d", minutes, rest);
    }

    /** "50 min de 1 h (83 %)"; sin estimación, solo "50 min". */
    public static String trackedVsEstimate(int tracked, Integer estimated) {
        String real = tracked <= 0 ? "0 min" : duration(tracked);
        if (estimated == null || estimated <= 0) {
            return real;
        }
        return real + " de " + duration(estimated) + " (" + Math.round(100.0 * tracked / estimated) + " %)";
    }

    /** "45 min", "2 h", "1 h 30 min"; vacío si no hay duración. */
    public static String duration(Integer minutes) {
        if (minutes == null || minutes <= 0) {
            return "";
        }
        int hours = minutes / 60;
        int rest = minutes % 60;
        if (hours == 0) {
            return rest + " min";
        }
        return rest == 0 ? hours + " h" : hours + " h " + rest + " min";
    }

    /** "Semana 42 · 12–18 oct" (o "28 sept – 4 oct" si cambia de mes). */
    public static String weekTitle(LocalDate monday) {
        LocalDate sunday = monday.plusDays(6);
        int week = monday.get(WeekFields.ISO.weekOfWeekBasedYear());
        String range = monday.getMonth() == sunday.getMonth()
                ? monday.getDayOfMonth() + "–" + dayMonth(sunday)
                : dayMonth(monday) + " – " + dayMonth(sunday);
        return "Semana " + week + " · " + range;
    }

    /** Interpreta "9", "930", "9:30" o "09:30" como hora; vacío o inválido → vacío. */
    public static Optional<LocalTime> parseTime(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String value = text.trim();
        Matcher colon = TIME_WITH_COLON.matcher(value);
        Matcher digits = TIME_DIGITS.matcher(value);
        int hour;
        int minute;
        if (colon.matches()) {
            hour = Integer.parseInt(colon.group(1));
            minute = Integer.parseInt(colon.group(2));
        } else if (digits.matches()) {
            hour = Integer.parseInt(digits.group(1));
            minute = digits.group(2) == null ? 0 : Integer.parseInt(digits.group(2));
        } else {
            return Optional.empty();
        }
        if (hour > 23 || minute > 59) {
            return Optional.empty();
        }
        return Optional.of(LocalTime.of(hour, minute));
    }

    public static LocalDate mondayOf(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    public static String capitalize(String text) {
        return text == null || text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
