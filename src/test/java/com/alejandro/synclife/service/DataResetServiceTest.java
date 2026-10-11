package com.alejandro.synclife.service;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.TestSupport;
import com.alejandro.synclife.config.AppSettings;
import com.alejandro.synclife.config.AppSettings.ThemeMode;
import com.alejandro.synclife.model.TaskFilter;
import com.alejandro.synclife.model.entity.Category;
import com.alejandro.synclife.model.entity.Goal;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.service.DataResetService.Summary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static com.alejandro.synclife.TestSupport.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataResetServiceTest {

    private static final List<String> DEFAULT_CATEGORIES = List.of("Reuniones", "Proyectos", "Correo y gestión",
            "Formación", "Gimnasio", "Correr", "Estiramientos", "Casa", "Recados", "Personal", "Salud");

    @TempDir
    Path dir;
    AppContext ctx;

    @BeforeEach
    void setUp() {
        ctx = TestSupport.context(dir);
        // De todo un poco: tareas con checklist y tiempo, rutinas, objetivos, revisión con prioridades,
        // categorías propias y una de serie renombrada
        DemoData.seed(ctx);
        Task task = ctx.tasks().quickAdd("Tarea con tiempo", Area.TRABAJO, TODAY, null);
        ctx.timeTracking().addManual(task.getId(), 20);
        Goal goal = ctx.goals().save(new Goal("Objetivo para borrar", Area.VIDA));
        ctx.goals().link(goal.getId(), List.of(task.getId()));
        ctx.reviews().save(ctx.reviews().defaultPlannedMonday(), "Notas", List.of(task.getId()));
        ctx.categories().save(new Category("Inventada", Area.VIDA, "#123456"));
        Category meetings = ctx.categories().findAll(true).stream()
                .filter(c -> c.getName().equals("Reuniones")).findFirst().orElseThrow();
        meetings.setName("Reuniones renombradas");
        ctx.categories().save(meetings);
    }

    @AfterEach
    void tearDown() {
        ctx.close();
    }

    @Test
    void leavesTheDatabaseLikeANewInstall() throws Exception {
        Summary before = ctx.dataReset().summary();
        assertThat(before.tasks()).isPositive();
        assertThat(before.routines()).isPositive();
        assertThat(before.goals()).isPositive();
        assertThat(before.reviews()).isPositive();

        ctx.dataReset().resetAll();

        // Ninguna tabla conserva datos (salvo las categorías de serie): vigila también las tablas de migraciones futuras
        for (String table : TestSupport.column(ctx, "select name from sqlite_master where type = 'table' "
                + "and name not like 'sqlite_%' and name <> 'flyway_schema_history' and name <> 'category'")) {
            assertThat(TestSupport.single(ctx, "select count(*) from " + table)).as(table).isEqualTo("0");
        }
        assertThat(ctx.categories().findAll(true)).extracting(Category::getName)
                .containsExactlyInAnyOrderElementsOf(DEFAULT_CATEGORIES);
        assertThat(ctx.dataReset().summary()).isEqualTo(new Summary(0, 0, 0, 0, DEFAULT_CATEGORIES.size()));
        assertThat(ctx.dataReset().summary().isEmpty()).isTrue();
        // El historial de migraciones sigue intacto: al reabrir no se vuelve a migrar nada
        assertThat(TestSupport.single(ctx, "select max(version) from flyway_schema_history")).isEqualTo("8");
    }

    @Test
    void keepsACopyOfEverythingBeforeDeleting() throws Exception {
        long tasksBefore = ctx.dataReset().summary().tasks();

        Path backup = ctx.dataReset().resetAll();

        assertThat(backup).exists().hasParent(dir.resolve("copias"));
        assertThat(backup.getFileName().toString()).isEqualTo("synclife-antes-de-borrar-2026-10-12-090000.db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + backup);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("select count(*) from task")) {
            rs.next();
            assertThat(rs.getLong(1)).isEqualTo(tasksBefore);
        }
    }

    @Test
    void theAppKeepsWorkingAndIdsStartAgain() {
        ctx.dataReset().resetAll();

        Category projects = ctx.categories().findAll(true).stream()
                .filter(c -> c.getName().equals("Proyectos")).findFirst().orElseThrow();
        Task task = new Task("Primera tarea", Area.TRABAJO);
        task.setCategory(projects);
        task.setScheduledDate(TODAY);
        Task saved = ctx.tasks().save(task);

        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(ctx.tasks().search(TaskFilter.all())).singleElement()
                .satisfies(t -> assertThat(t.getCategory().getName()).isEqualTo("Proyectos"));
        DemoData.seed(ctx); // y se pueden volver a cargar los datos de ejemplo
        assertThat(ctx.dataReset().summary().routines()).isPositive();
    }

    @Test
    void settingsAreKept() throws Exception {
        ctx.settings().setTheme(ThemeMode.OSCURO);
        ctx.settings().save();

        ctx.dataReset().resetAll();

        assertThat(AppSettings.load(dir).getTheme()).isEqualTo(ThemeMode.OSCURO);
    }

    @Test
    void withoutABackupNothingIsDeleted() throws Exception {
        Files.writeString(dir.resolve("copias"), "un fichero donde debería ir la carpeta");
        long tasksBefore = ctx.dataReset().summary().tasks();

        assertThatThrownBy(() -> ctx.dataReset().resetAll())
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("no se ha borrado nada");
        assertThat(ctx.dataReset().summary().tasks()).isEqualTo(tasksBefore);
    }
}
