package com.alejandro.synclife.persistence;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.TestSupport;
import com.alejandro.synclife.model.ChecklistItem;
import com.alejandro.synclife.model.entity.Goal;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Area;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationTest {

    @TempDir
    Path dir;
    AppContext ctx;

    @BeforeEach
    void setUp() {
        ctx = TestSupport.context(dir);
    }

    @AfterEach
    void tearDown() {
        ctx.close();
    }

    @Test
    void flywayAppliesAllMigrationsAndSeedsCategories() {
        assertThat(TestSupport.single(ctx, "select max(cast(version as integer)) from flyway_schema_history"))
                .isEqualTo("7");
        assertThat(TestSupport.column(ctx, "select area || ':' || count(*) from category group by area order by area"))
                .containsExactly("DEPORTE:3", "TRABAJO:4", "VIDA:4");
    }

    @Test
    void reopeningTheDatabaseDoesNotReapplyMigrations() {
        ctx.close();
        ctx = TestSupport.context(dir);
        assertThat(TestSupport.single(ctx, "select count(*) from category")).isEqualTo("11");
    }

    @Test
    void hibernateConnectionsHaveForeignKeysEnabled() {
        int foreignKeys = ctx.sessionFactory().fromSession(s -> s.doReturningWork(connection -> {
            try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("PRAGMA foreign_keys")) {
                rs.next();
                return rs.getInt(1);
            }
        }));
        assertThat(foreignKeys).isEqualTo(1);
    }

    @Test
    void routineDayUniqueIndexExists() {
        assertThat(TestSupport.single(ctx,
                "select count(*) from sqlite_master where type = 'index' and name = 'ux_task_routine_day'"))
                .isEqualTo("1");
    }

    @Test
    void datesAndTimesAreStoredAsIsoText() {
        Task task = new Task("Revisar contrato", Area.TRABAJO);
        task.setScheduledDate(LocalDate.of(2026, 10, 12));
        task.setStartTime(LocalTime.of(19, 0));
        task.setDueDate(LocalDate.of(2026, 10, 20));
        Task saved = ctx.tasks().save(task);
        ctx.tasks().toggleDone(saved.getId());

        String row = TestSupport.single(ctx,
                "select scheduled_date || '|' || start_time || '|' || due_date || '|' || priority || '|' || status "
                        + "from task where id = " + saved.getId());
        assertThat(row).isEqualTo("2026-10-12|19:00|2026-10-20|2|COMPLETADA");
        assertThat(TestSupport.single(ctx, "select completed_at from task where id = " + saved.getId()))
                .isEqualTo("2026-10-12T09:00:00");
        assertThat(TestSupport.single(ctx, "select typeof(scheduled_date) from task where id = " + saved.getId()))
                .isEqualTo("text");
    }

    @Test
    void datesSurviveAJvmTimeZoneChange() {
        Task task = new Task("Viaje", Area.VIDA);
        task.setScheduledDate(LocalDate.of(2026, 3, 29)); // cambio de hora en Europa
        task.setStartTime(LocalTime.of(2, 30));
        long id = ctx.tasks().save(task).getId();

        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            Task reloaded = ctx.tasks().findById(id).orElseThrow();
            assertThat(reloaded.getScheduledDate()).isEqualTo(LocalDate.of(2026, 3, 29));
            assertThat(reloaded.getStartTime()).isEqualTo(LocalTime.of(2, 30));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void deletingATaskCascadesToItsChecklistAndTimeEntries() {
        Task task = ctx.tasks().save(new Task("Informe", Area.TRABAJO),
                List.of(new ChecklistItem(null, "Datos", false), new ChecklistItem(null, "Gráficos", true)));
        ctx.timeTracking().addManual(task.getId(), 30);

        ctx.tasks().delete(task.getId());

        assertThat(TestSupport.single(ctx, "select count(*) from subtask")).isEqualTo("0");
        assertThat(TestSupport.single(ctx, "select count(*) from time_entry")).isEqualTo("0");
    }

    @Test
    void existingTasksStayUnclassifiedInTheMatrix() throws Exception {
        // Fila insertada "a mano" como la habría dejado la versión anterior (sin urgent/important)
        TestSupport.execute(ctx, "insert into task (title, area, created_at, updated_at) "
                + "values ('Antigua', 'TRABAJO', '2026-01-01T10:00:00', '2026-01-01T10:00:00')");
        assertThat(TestSupport.single(ctx,
                "select coalesce(urgent, 'NULL') || '|' || coalesce(important, 'NULL') from task where title = 'Antigua'"))
                .isEqualTo("NULL|NULL");
        Task loaded = ctx.tasks().matrix(null).unclassified().get(0);
        assertThat(loaded.getTitle()).isEqualTo("Antigua");
        assertThat(loaded.getQuadrant()).isNull();
    }

    @Test
    void timeEntryConstraintsRejectInvalidRows() {
        Task task = ctx.tasks().save(new Task("Informe", Area.TRABAJO));
        assertThatThrownBy(() -> TestSupport.execute(ctx,
                "insert into time_entry (task_id, started_at, ended_at, minutes, source, created_at, updated_at) "
                        + "values (" + task.getId() + ", '2026-10-12T10:00:00', '2026-10-12T09:00:00', 10, "
                        + "'MANUAL', '2026-10-12T10:00:00', '2026-10-12T10:00:00')"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> TestSupport.execute(ctx,
                "insert into time_entry (task_id, started_at, ended_at, minutes, source, created_at, updated_at) "
                        + "values (" + task.getId() + ", '2026-10-12T09:00:00', '2026-10-12T10:00:00', 0, "
                        + "'MANUAL', '2026-10-12T10:00:00', '2026-10-12T10:00:00')"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void deletingAGoalKeepsItsTasksWithoutGoal() {
        Goal goal = ctx.goals().save(new Goal("Correr una 10K", Area.DEPORTE));
        Task task = ctx.tasks().save(new Task("Correr 5 km", Area.DEPORTE));
        ctx.goals().link(goal.getId(), List.of(task.getId()));

        ctx.goals().delete(goal.getId());

        assertThat(TestSupport.single(ctx, "select coalesce(goal_id, 'NULL') from task where id = " + task.getId()))
                .isEqualTo("NULL");
        assertThat(ctx.tasks().findById(task.getId()).orElseThrow().getGoal()).isNull();
    }

    @Test
    void aReviewWeekMustStartOnMonday() {
        assertThatThrownBy(() -> TestSupport.execute(ctx,
                "insert into weekly_review (week_start, created_at, updated_at) "
                        + "values ('2026-10-13', '2026-10-12T10:00:00', '2026-10-12T10:00:00')"))
                .isInstanceOf(SQLException.class);
        assertThatCode(() -> TestSupport.execute(ctx,
                "insert into weekly_review (week_start, created_at, updated_at) "
                        + "values ('2026-10-12', '2026-10-12T10:00:00', '2026-10-12T10:00:00')"))
                .doesNotThrowAnyException();
    }

    @Test
    void backupCreatesACopyOfTheDatabase() throws Exception {
        Path target = dir.resolve("copia.db");
        DatabaseBackup.backupTo(ctx.jdbcUrl(), target);
        assertThat(target).exists();
        assertThat(java.nio.file.Files.size(target)).isPositive();
    }
}
