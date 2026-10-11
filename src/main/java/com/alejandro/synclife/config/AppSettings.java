package com.alejandro.synclife.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Preferencias del usuario guardadas en {@code settings.properties} dentro de la carpeta de datos. */
public final class AppSettings {

    public enum ThemeMode { CLARO, OSCURO }

    public static final int MIN_HORIZON = 7;
    public static final int MAX_HORIZON = 60;
    public static final int DEFAULT_HORIZON = 14;

    private static final Logger LOG = Logger.getLogger(AppSettings.class.getName());
    private static final String FILE_NAME = "settings.properties";

    /** Ajustes del Pomodoro, en minutos (salvo {@code longBreakEvery}, en número de enfoques). */
    public record PomodoroSettings(int focusMinutes, int shortBreakMinutes, int longBreakMinutes,
                                   int longBreakEvery, boolean autoStartBreaks, boolean sound) {

        public static final PomodoroSettings DEFAULT = new PomodoroSettings(25, 5, 15, 4, true, true);

        /** Los mismos ajustes, con cada valor dentro de su rango permitido. */
        public PomodoroSettings clamped() {
            return new PomodoroSettings(clamp(focusMinutes, 5, 90), clamp(shortBreakMinutes, 1, 30),
                    clamp(longBreakMinutes, 5, 60), clamp(longBreakEvery, 2, 8), autoStartBreaks, sound);
        }
    }

    private final Path file;
    private ThemeMode theme = ThemeMode.CLARO;
    private int routineHorizonDays = DEFAULT_HORIZON;
    private PomodoroSettings pomodoro = PomodoroSettings.DEFAULT;

    private AppSettings(Path file) {
        this.file = file;
    }

    public static AppSettings load(Path dataDir) {
        AppSettings settings = new AppSettings(dataDir.resolve(FILE_NAME));
        if (Files.exists(settings.file)) {
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(settings.file)) {
                props.load(in);
                settings.theme = parseTheme(props.getProperty("theme"));
                settings.routineHorizonDays = clampHorizon(parseInt(props.getProperty("routine.horizon.days")));
                PomodoroSettings d = PomodoroSettings.DEFAULT;
                settings.pomodoro = new PomodoroSettings(
                        parseInt(props.getProperty("pomodoro.focus.minutes"), d.focusMinutes()),
                        parseInt(props.getProperty("pomodoro.shortBreak.minutes"), d.shortBreakMinutes()),
                        parseInt(props.getProperty("pomodoro.longBreak.minutes"), d.longBreakMinutes()),
                        parseInt(props.getProperty("pomodoro.longBreak.every"), d.longBreakEvery()),
                        parseBoolean(props.getProperty("pomodoro.autoStartBreaks"), d.autoStartBreaks()),
                        parseBoolean(props.getProperty("pomodoro.sound"), d.sound())).clamped();
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "No se pudieron leer los ajustes; se usan los valores por defecto", e);
            }
        }
        return settings;
    }

    public synchronized void save() throws IOException {
        Properties props = new Properties();
        props.setProperty("theme", theme.name());
        props.setProperty("routine.horizon.days", Integer.toString(routineHorizonDays));
        props.setProperty("pomodoro.focus.minutes", Integer.toString(pomodoro.focusMinutes()));
        props.setProperty("pomodoro.shortBreak.minutes", Integer.toString(pomodoro.shortBreakMinutes()));
        props.setProperty("pomodoro.longBreak.minutes", Integer.toString(pomodoro.longBreakMinutes()));
        props.setProperty("pomodoro.longBreak.every", Integer.toString(pomodoro.longBreakEvery()));
        props.setProperty("pomodoro.autoStartBreaks", Boolean.toString(pomodoro.autoStartBreaks()));
        props.setProperty("pomodoro.sound", Boolean.toString(pomodoro.sound()));
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "Ajustes de SyncLife");
        }
    }

    public synchronized ThemeMode getTheme() {
        return theme;
    }

    public synchronized void setTheme(ThemeMode theme) {
        this.theme = theme == null ? ThemeMode.CLARO : theme;
    }

    public synchronized int getRoutineHorizonDays() {
        return routineHorizonDays;
    }

    public synchronized void setRoutineHorizonDays(int days) {
        this.routineHorizonDays = clampHorizon(days);
    }

    public synchronized PomodoroSettings getPomodoro() {
        return pomodoro;
    }

    public synchronized void setPomodoro(PomodoroSettings pomodoro) {
        this.pomodoro = pomodoro == null ? PomodoroSettings.DEFAULT : pomodoro.clamped();
    }

    private static ThemeMode parseTheme(String value) {
        try {
            return value == null ? ThemeMode.CLARO : ThemeMode.valueOf(value);
        } catch (IllegalArgumentException e) {
            return ThemeMode.CLARO;
        }
    }

    private static int parseInt(String value) {
        return parseInt(value, DEFAULT_HORIZON);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }

    private static int clampHorizon(int days) {
        return clamp(days, MIN_HORIZON, MAX_HORIZON);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
