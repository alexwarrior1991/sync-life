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

    private final Path file;
    private ThemeMode theme = ThemeMode.CLARO;
    private int routineHorizonDays = DEFAULT_HORIZON;

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

    private static ThemeMode parseTheme(String value) {
        try {
            return value == null ? ThemeMode.CLARO : ThemeMode.valueOf(value);
        } catch (IllegalArgumentException e) {
            return ThemeMode.CLARO;
        }
    }

    private static int parseInt(String value) {
        try {
            return value == null ? DEFAULT_HORIZON : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_HORIZON;
        }
    }

    private static int clampHorizon(int days) {
        return Math.max(MIN_HORIZON, Math.min(MAX_HORIZON, days));
    }
}
