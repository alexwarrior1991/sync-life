package com.alejandro.synclife.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Logging con java.util.logging: avisos de Hibernate/Flyway a WARNING y un fichero rotativo
 * en {@code <datos>/logs/synclife0.log}.
 */
public final class LoggingConfig {

    // Referencias fuertes: si el logger se recolecta, se pierde el nivel configurado.
    private static final Logger HIBERNATE = Logger.getLogger("org.hibernate");
    private static final Logger FLYWAY = Logger.getLogger("org.flywaydb");
    // El pool interno con una sola conexión es intencionado (SQLite + un único hilo de BD): sin aviso
    private static final Logger HIBERNATE_POOL = Logger.getLogger("org.hibernate.orm.connections.pooling");
    private static final Logger ROOT = Logger.getLogger("");

    private static boolean initialized;

    private LoggingConfig() {
    }

    public static synchronized void init(Path dataDir) {
        if (initialized) {
            return;
        }
        initialized = true;
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%1$tF %1$tT %4$-7s [%3$s] %5$s%6$s%n");
        HIBERNATE.setLevel(Level.WARNING);
        FLYWAY.setLevel(Level.WARNING);
        HIBERNATE_POOL.setLevel(Level.SEVERE);
        try {
            Path logs = AppPaths.logsDir(dataDir);
            Files.createDirectories(logs);
            FileHandler file = new FileHandler(logs.resolve("synclife%g.log").toString(), 1_000_000, 3, true);
            file.setFormatter(new SimpleFormatter());
            file.setEncoding("UTF-8");
            ROOT.addHandler(file);
        } catch (IOException e) {
            ROOT.log(Level.WARNING, "No se pudo crear el fichero de log", e);
        }
    }
}
