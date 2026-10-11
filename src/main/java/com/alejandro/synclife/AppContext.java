package com.alejandro.synclife;

import com.alejandro.synclife.config.AppPaths;
import com.alejandro.synclife.config.AppSettings;
import com.alejandro.synclife.dao.CategoryDao;
import com.alejandro.synclife.dao.RoutineDao;
import com.alejandro.synclife.dao.SubtaskDao;
import com.alejandro.synclife.dao.TaskDao;
import com.alejandro.synclife.dao.TimeEntryDao;
import com.alejandro.synclife.persistence.DatabaseMigrator;
import com.alejandro.synclife.persistence.HibernateUtil;
import com.alejandro.synclife.service.CategoryService;
import com.alejandro.synclife.service.RoutineService;
import com.alejandro.synclife.service.StatsService;
import com.alejandro.synclife.service.TaskService;
import com.alejandro.synclife.service.TimeTrackingService;
import org.hibernate.SessionFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Raíz de composición: crea la base de datos, Hibernate, DAOs y servicios, y los pone a disposición
 * de los controladores. No hay framework de inyección: todo se conecta a mano aquí.
 */
public final class AppContext implements AutoCloseable {

    private static volatile AppContext current;

    private final Path dataDir;
    private final String jdbcUrl;
    private final Clock clock;
    private final AppSettings settings;
    private final SessionFactory sessionFactory;
    private final TaskService taskService;
    private final CategoryService categoryService;
    private final RoutineService routineService;
    private final StatsService statsService;
    private final TimeTrackingService timeTrackingService;

    private AppContext(Path dataDir, Clock clock) {
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear la carpeta de datos " + dataDir, e);
        }
        this.dataDir = dataDir;
        this.clock = clock;
        this.settings = AppSettings.load(dataDir);
        this.jdbcUrl = AppPaths.jdbcUrl(AppPaths.dbFile(dataDir));

        DatabaseMigrator.migrate(jdbcUrl);
        this.sessionFactory = HibernateUtil.build(jdbcUrl);

        TaskDao taskDao = new TaskDao();
        CategoryDao categoryDao = new CategoryDao();
        RoutineDao routineDao = new RoutineDao();
        this.taskService = new TaskService(sessionFactory, taskDao, new SubtaskDao(), clock);
        this.categoryService = new CategoryService(sessionFactory, categoryDao, taskDao);
        this.routineService = new RoutineService(sessionFactory, routineDao, taskDao, clock,
                settings::getRoutineHorizonDays);
        this.timeTrackingService = new TimeTrackingService(sessionFactory, new TimeEntryDao(), clock);
        this.statsService = new StatsService(taskService, timeTrackingService, clock);
    }

    /** Crea un contexto independiente (usado por los tests). */
    public static AppContext create(Path dataDir, Clock clock) {
        AppContext context = new AppContext(dataDir, clock);
        context.routineService.ensureHorizon();
        return context;
    }

    /** Crea el contexto de la aplicación y lo publica para {@link #get()}. */
    public static AppContext bootstrap(Path dataDir, Clock clock) {
        current = create(dataDir, clock);
        return current;
    }

    public static AppContext get() {
        AppContext context = current;
        if (context == null) {
            throw new IllegalStateException("AppContext no inicializado");
        }
        return context;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public Path dataDir() {
        return dataDir;
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public Clock clock() {
        return clock;
    }

    public AppSettings settings() {
        return settings;
    }

    public SessionFactory sessionFactory() {
        return sessionFactory;
    }

    public TaskService tasks() {
        return taskService;
    }

    public CategoryService categories() {
        return categoryService;
    }

    public RoutineService routines() {
        return routineService;
    }

    public StatsService stats() {
        return statsService;
    }

    public TimeTrackingService timeTracking() {
        return timeTrackingService;
    }

    @Override
    public void close() {
        if (sessionFactory.isOpen()) {
            sessionFactory.close();
        }
        if (current == this) {
            current = null;
        }
    }
}
