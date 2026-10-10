package com.alejandro.synclife.service;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.TestSupport;
import com.alejandro.synclife.model.DaysOfWeek;
import com.alejandro.synclife.model.entity.Routine;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.TaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static com.alejandro.synclife.TestSupport.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutineServiceTest {

    private static final int LXV = 0b0010101;

    @TempDir
    Path dir;
    AppContext ctx;
    RoutineService routines;

    @BeforeEach
    void setUp() {
        ctx = TestSupport.context(dir);
        routines = ctx.routines();
    }

    @AfterEach
    void tearDown() {
        ctx.close();
    }

    private List<Task> instancesOf(Routine routine) {
        return ctx.tasks().tasksBetween(TODAY.minusDays(30), TODAY.plusDays(60)).stream()
                .filter(t -> t.getRoutine() != null && t.getRoutine().getId().equals(routine.getId()))
                .toList();
    }

    private void reopenOn(LocalDate today) {
        ctx.close();
        ctx = TestSupport.context(dir, today);
        routines = ctx.routines();
    }

    @Test
    void savingARoutineGeneratesInstancesUpToTheHorizon() {
        Routine gym = routines.save(new Routine("Gimnasio", Area.DEPORTE, LXV, TODAY));
        // Horizonte por defecto: 14 días desde el lunes 12 → L-X-V de dos semanas
        assertThat(instancesOf(gym)).extracting(Task::getScheduledDate).containsExactly(
                TODAY, TODAY.plusDays(2), TODAY.plusDays(4),
                TODAY.plusDays(7), TODAY.plusDays(9), TODAY.plusDays(11));
        assertThat(routines.ensureHorizon()).isZero();
    }

    @Test
    void instancesCopyTheRoutineData() {
        Routine gym = new Routine("Gimnasio", Area.DEPORTE, DaysOfWeek.ALL, TODAY);
        gym.setStartTime(LocalTime.of(19, 0));
        gym.setDurationMinutes(60);
        routines.save(gym);

        Task today = ctx.tasks().tasksForDay(TODAY).get(0);
        assertThat(today.getTitle()).isEqualTo("Gimnasio");
        assertThat(today.getStartTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(today.getEstimatedMinutes()).isEqualTo(60);
        assertThat(today.getArea()).isEqualTo(Area.DEPORTE);
    }

    @Test
    void uniqueIndexRejectsASecondInstanceForTheSameDay() {
        Routine r = routines.save(new Routine("Correo", Area.TRABAJO, DaysOfWeek.ALL, TODAY));
        assertThatThrownBy(() -> TestSupport.execute(ctx,
                "insert into task (title, area, routine_id, status, priority, scheduled_date, created_at, updated_at) "
                        + "values ('dup', 'TRABAJO', " + r.getId() + ", 'PENDIENTE', 2, '" + TODAY
                        + "', '2026-10-12T09:00:00', '2026-10-12T09:00:00')"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("UNIQUE");
    }

    @Test
    void neverGeneratesPastDays() {
        Routine r = new Routine("Diaria", Area.VIDA, DaysOfWeek.ALL, TODAY.minusDays(10));
        routines.save(r);
        assertThat(instancesOf(r)).allMatch(t -> !t.getScheduledDate().isBefore(TODAY));
        assertThat(routines.ensureInstances(TODAY.minusDays(7), TODAY.minusDays(1))).isZero();
    }

    @Test
    void respectsEndDateAndInactiveRoutines() {
        Routine shortOne = new Routine("Corta", Area.TRABAJO, DaysOfWeek.ALL, TODAY);
        shortOne.setEndDate(TODAY.plusDays(2));
        assertThat(instancesOf(routines.save(shortOne))).hasSize(3);

        Routine paused = new Routine("Pausada", Area.VIDA, DaysOfWeek.ALL, TODAY);
        paused.setActive(false);
        paused = routines.save(paused);
        assertThat(instancesOf(paused)).isEmpty();

        routines.setActive(paused.getId(), true);
        assertThat(instancesOf(paused)).hasSize(14);

        routines.setActive(paused.getId(), false);
        assertThat(instancesOf(paused)).isEmpty();
    }

    @Test
    void editingKeepsCompletedAndSkippedInstances() {
        Routine r = routines.save(new Routine("Lectura", Area.VIDA, DaysOfWeek.ALL, TODAY));
        List<Task> before = instancesOf(r);
        ctx.tasks().toggleDone(before.get(0).getId()); // hoy: hecha
        ctx.tasks().skip(before.get(1).getId());       // mañana: omitida

        r.setStartTime(LocalTime.of(7, 0));
        routines.save(r);

        List<Task> after = instancesOf(r);
        assertThat(after).hasSize(14);
        assertThat(after.get(0).getStatus()).isEqualTo(TaskStatus.COMPLETADA);
        assertThat(after.get(0).getStartTime()).isNull();
        assertThat(after.get(1).getStatus()).isEqualTo(TaskStatus.CANCELADA);
        assertThat(after.get(2).getStartTime()).isEqualTo(LocalTime.of(7, 0));
    }

    @Test
    void changingDaysRemovesPendingInstancesThatNoLongerApply() {
        Routine r = routines.save(new Routine("Inglés", Area.TRABAJO, DaysOfWeek.ALL, TODAY));
        r.setDaysMask(DaysOfWeek.WEEKEND);
        routines.save(r);
        assertThat(instancesOf(r)).hasSize(4)
                .allMatch(t -> t.getScheduledDate().getDayOfWeek().getValue() >= 6);
    }

    @Test
    void movingForwardInTimeGeneratesNewDaysWithoutDuplicatingOrCarryingOver() {
        Routine r = routines.save(new Routine("Estiramientos", Area.DEPORTE, DaysOfWeek.ALL, TODAY));
        assertThat(instancesOf(r)).hasSize(14);

        LocalDate later = TODAY.plusDays(3);
        reopenOn(later); // al arrancar la app se completa el horizonte
        assertThat(instancesOf(r)).hasSize(17);
        // Las instancias pasadas sin hacer no aparecen como "pendientes de días anteriores"
        assertThat(ctx.tasks().carryOver()).isEmpty();
    }

    @Test
    void deletingARoutineKeepsHistory() {
        Routine r = routines.save(new Routine("Meditar", Area.VIDA, DaysOfWeek.ALL, TODAY));
        Task first = instancesOf(r).get(0);
        ctx.tasks().toggleDone(first.getId());

        reopenOn(TODAY.plusDays(2)); // el día 13 quedó sin hacer
        routines.delete(r.getId());

        assertThat(ctx.routines().findAll()).isEmpty();
        List<Task> remaining = ctx.tasks().tasksBetween(TODAY, TODAY.plusDays(30));
        assertThat(remaining).extracting(Task::getStatus)
                .containsExactly(TaskStatus.COMPLETADA, TaskStatus.CANCELADA);
        assertThat(remaining).allMatch(t -> t.getRoutine() == null);
        assertThat(ctx.tasks().carryOver()).isEmpty();
    }

    @Test
    void validatesRoutine() {
        assertThatThrownBy(() -> routines.save(new Routine("", Area.VIDA, DaysOfWeek.ALL, TODAY)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> routines.save(new Routine("Sin días", Area.VIDA, 0, TODAY)))
                .isInstanceOf(ValidationException.class);
        Routine badDates = new Routine("Fechas", Area.VIDA, DaysOfWeek.ALL, TODAY);
        badDates.setEndDate(TODAY.minusDays(1));
        assertThatThrownBy(() -> routines.save(badDates)).isInstanceOf(ValidationException.class);
    }

    @Test
    void statsCountCompletionAndAdherence() {
        Routine r = routines.save(new Routine("Agua", Area.VIDA, DaysOfWeek.ALL, TODAY));
        ctx.tasks().toggleDone(instancesOf(r).get(0).getId());
        Task work = new Task("Informe", Area.TRABAJO);
        work.setScheduledDate(TODAY);
        work.setEstimatedMinutes(90);
        ctx.tasks().toggleDone(ctx.tasks().save(work).getId());

        StatsService.Snapshot snapshot = ctx.stats().compute(TODAY, TODAY.plusDays(6));
        assertThat(snapshot.completed()).isEqualTo(2);
        assertThat(snapshot.total()).isEqualTo(8); // 7 instancias + 1 tarea
        assertThat(snapshot.minutesCompleted()).isEqualTo(90);
        assertThat(snapshot.byDay()).hasSize(7);
        assertThat(snapshot.routines()).singleElement().satisfies(a -> {
            assertThat(a.routine()).isEqualTo("Agua");
            assertThat(a.completed()).isEqualTo(1);
            assertThat(a.total()).isEqualTo(1); // solo cuenta hasta hoy
        });
    }
}
