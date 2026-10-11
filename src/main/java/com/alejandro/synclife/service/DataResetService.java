package com.alejandro.synclife.service;

import com.alejandro.synclife.config.AppPaths;
import com.alejandro.synclife.persistence.DatabaseBackup;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * "Borrar todos los datos": deja la base de datos como recién instalada (sin tareas, rutinas, objetivos ni
 * revisiones, y con las categorías de serie). Antes guarda una copia completa en {@code <datos>/copias}, y si
 * no puede guardarla no borra nada. Los ajustes ({@code settings.properties}) no se tocan.
 */
public class DataResetService {

    /** Lo que hay ahora (para el diálogo de confirmación). Las tareas incluyen las generadas por rutinas. */
    public record Summary(long tasks, long routines, long goals, long reviews, long categories) {
        public boolean isEmpty() {
            return tasks + routines + goals + reviews == 0;
        }
    }

    /**
     * Tablas con datos del usuario, de hijas a padres. Una migración que añada una tabla de datos debe añadirla
     * aquí: {@code DataResetServiceTest} comprueba que tras borrar no queda nada en ninguna tabla.
     */
    static final List<String> TABLES = List.of("weekly_priority", "weekly_review", "time_entry", "subtask", "task",
            "routine", "goal", "category");

    /** Las categorías de serie son las que inserta esta migración: se reutiliza para no duplicar la lista. */
    private static final String DEFAULT_CATEGORIES = "/db/migration/V2__categorias_por_defecto.sql";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

    private final SessionFactory sessionFactory;
    private final String jdbcUrl;
    private final Path dataDir;
    private final Clock clock;

    public DataResetService(SessionFactory sessionFactory, String jdbcUrl, Path dataDir, Clock clock) {
        this.sessionFactory = sessionFactory;
        this.jdbcUrl = jdbcUrl;
        this.dataDir = dataDir;
        this.clock = clock;
    }

    public Summary summary() {
        return sessionFactory.fromTransaction(s -> new Summary(count(s, "Task"), count(s, "Routine"),
                count(s, "Goal"), count(s, "WeeklyReview"), count(s, "Category")));
    }

    /** Guarda la copia, borra todo en una transacción y devuelve dónde quedó la copia. */
    public Path resetAll() {
        Path backup = AppPaths.backupsDir(dataDir)
                .resolve("synclife-antes-de-borrar-" + STAMP.format(LocalDateTime.now(clock)) + ".db");
        try {
            Files.createDirectories(backup.getParent());
            DatabaseBackup.backupTo(jdbcUrl, backup);
        } catch (IOException | SQLException e) {
            throw new ValidationException("No se pudo guardar la copia de seguridad, así que no se ha borrado nada ("
                    + e.getMessage() + ").", e);
        }
        String defaultCategories = defaultCategoriesSql();
        String tableList = TABLES.stream().map(t -> "'" + t + "'").collect(Collectors.joining(", "));
        sessionFactory.inTransaction(session -> session.doWork(connection -> {
            try (Statement statement = connection.createStatement()) {
                for (String table : TABLES) {
                    statement.executeUpdate("DELETE FROM " + table);
                }
                // Los ids vuelven a empezar por 1, como en una instalación nueva
                statement.executeUpdate("DELETE FROM sqlite_sequence WHERE name IN (" + tableList + ")");
                statement.executeUpdate(defaultCategories);
            }
        }));
        return backup;
    }

    private static long count(Session session, String entity) {
        return session.createSelectionQuery("select count(*) from " + entity, Long.class).getSingleResult();
    }

    private static String defaultCategoriesSql() {
        try (InputStream in = DataResetService.class.getResourceAsStream(DEFAULT_CATEGORIES)) {
            if (in == null) {
                throw new IllegalStateException("No se encuentra " + DEFAULT_CATEGORIES);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
