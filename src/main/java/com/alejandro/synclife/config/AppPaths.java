package com.alejandro.synclife.config;

import java.nio.file.Path;

/**
 * Ubicación de los datos del usuario. Por defecto {@code ~/.synclife}; se puede cambiar con
 * {@code -Dsynclife.home=/ruta} o la variable de entorno {@code SYNCLIFE_HOME}.
 */
public final class AppPaths {

    public static final String HOME_PROPERTY = "synclife.home";
    public static final String HOME_ENV = "SYNCLIFE_HOME";

    private AppPaths() {
    }

    public static Path dataDir() {
        String fromProperty = System.getProperty(HOME_PROPERTY);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return Path.of(fromProperty).toAbsolutePath();
        }
        String fromEnv = System.getenv(HOME_ENV);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return Path.of(fromEnv).toAbsolutePath();
        }
        return Path.of(System.getProperty("user.home"), ".synclife");
    }

    public static Path dbFile(Path dataDir) {
        return dataDir.resolve("synclife.db");
    }

    public static Path logsDir(Path dataDir) {
        return dataDir.resolve("logs");
    }

    /**
     * URL JDBC con las pragmas que necesitamos en cada conexión:
     * claves foráneas activadas y espera de hasta 5 s si la base de datos está ocupada.
     */
    public static String jdbcUrl(Path dbFile) {
        String path = dbFile.toAbsolutePath().toString().replace('\\', '/');
        return "jdbc:sqlite:" + path + "?foreign_keys=on&busy_timeout=5000";
    }
}
