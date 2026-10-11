package com.alejandro.synclife.service;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.TestSupport;
import com.alejandro.synclife.model.DaysOfWeek;
import com.alejandro.synclife.model.entity.Routine;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.entity.TimeEntry;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.TaskStatus;
import com.alejandro.synclife.model.enums.TimeSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

import static com.alejandro.synclife.TestSupport.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeTrackingServiceTest {

    @TempDir
    Path dir;
    AppContext ctx;
    TimeTrackingService time;

    @BeforeEach
    void setUp() {
        ctx = TestSupport.context(dir);
        time = ctx.timeTracking();
    }

    @AfterEach
    void tearDown() {
        ctx.close();
    }

    private Task task(String title, Area area) {
        Task task = new Task(title, area);
        task.setScheduledDate(TODAY);
        return ctx.tasks().save(task);
    }

    @Test
    void recordedMinutesAddUpOnTheTask() {
        Task report = task("Informe", Area.TRABAJO);
        LocalDateTime nine = TODAY.atTime(9, 0);
        time.record(report.getId(), nine, nine.plusMinutes(25), 25, TimeSource.POMODORO);
        time.addManual(report.getId(), 40);

        Task reloaded = ctx.tasks().findById(report.getId()).orElseThrow();
        assertThat(reloaded.getTrackedMinutes()).isEqualTo(65);
        assertThat(ctx.tasks().tasksForDay(TODAY).get(0).getTrackedMinutes()).isEqualTo(65);
    }

    @Test
    void manualEntriesEndNowAndEntriesAreListedNewestFirst() {
        Task report = task("Informe", Area.TRABAJO);
        time.record(report.getId(), TODAY.atTime(7, 0), TODAY.atTime(7, 25), 25, TimeSource.POMODORO);
        TimeEntry manual = time.addManual(report.getId(), 30);

        // El reloj de los tests marca las 9:00
        assertThat(manual.getEndedAt()).isEqualTo(TODAY.atTime(9, 0));
        assertThat(manual.getStartedAt()).isEqualTo(TODAY.atTime(8, 30));
        assertThat(manual.getSource()).isEqualTo(TimeSource.MANUAL);
        assertThat(time.entries(report.getId())).extracting(TimeEntry::getMinutes).containsExactly(30, 25);
    }

    @Test
    void deletingAnEntrySubtractsItsMinutes() {
        Task report = task("Informe", Area.TRABAJO);
        TimeEntry entry = time.addManual(report.getId(), 30);
        time.addManual(report.getId(), 10);

        time.deleteEntry(entry.getId());

        assertThat(ctx.tasks().findById(report.getId()).orElseThrow().getTrackedMinutes()).isEqualTo(10);
    }

    @Test
    void invalidEntriesAreRejected() {
        Task report = task("Informe", Area.TRABAJO);
        LocalDateTime nine = TODAY.atTime(9, 0);
        assertThatThrownBy(() -> time.record(report.getId(), nine, nine.minusMinutes(5), 5, TimeSource.MANUAL))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> time.addManual(report.getId(), 0)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> time.addManual(report.getId(), 24 * 60 + 1)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> time.addManual(9999, 10)).isInstanceOf(ValidationException.class);
    }

    @Test
    void minutesByAreaCountsSessionsStartedInTheRange() {
        Task report = task("Informe", Area.TRABAJO);
        Task gym = task("Gimnasio", Area.DEPORTE);
        time.record(report.getId(), TODAY.atTime(10, 0), TODAY.atTime(10, 25), 25, TimeSource.POMODORO);
        time.record(gym.getId(), TODAY.atTime(19, 0), TODAY.atTime(20, 0), 60, TimeSource.MANUAL);
        // Fuera del rango: el día anterior
        time.record(report.getId(), TODAY.minusDays(1).atTime(23, 0), TODAY.minusDays(1).atTime(23, 30), 30,
                TimeSource.MANUAL);

        Map<Area, Long> minutes = time.minutesByArea(TODAY, TODAY);
        assertThat(minutes).containsEntry(Area.TRABAJO, 25L).containsEntry(Area.DEPORTE, 60L)
                .containsEntry(Area.VIDA, 0L);
    }

    @Test
    void editingARoutineKeepsAnInstanceInProgressAndItsTrackedTime() {
        Routine gym = new Routine("Gimnasio", Area.DEPORTE, DaysOfWeek.ALL, TODAY);
        gym = ctx.routines().save(gym);
        Task today = ctx.tasks().tasksForDay(TODAY).get(0);
        Task working = ctx.tasks().startWorking(today.getId());
        assertThat(working.getStatus()).isEqualTo(TaskStatus.EN_PROGRESO);
        time.addManual(today.getId(), 20);

        gym.setStartTime(LocalTime.of(19, 0));
        ctx.routines().save(gym);

        Task after = ctx.tasks().tasksForDay(TODAY).get(0);
        assertThat(after.getId()).isEqualTo(today.getId());
        assertThat(after.getTrackedMinutes()).isEqualTo(20);
        assertThat(after.getStartTime()).isNull(); // no se regeneró
    }

    @Test
    void editingARoutineKeepsAPendingInstanceThatAlreadyHasTrackedTime() {
        Routine gym = ctx.routines().save(new Routine("Gimnasio", Area.DEPORTE, DaysOfWeek.ALL, TODAY));
        Task today = ctx.tasks().tasksForDay(TODAY).get(0);
        time.addManual(today.getId(), 15); // sigue PENDIENTE

        gym.setDurationMinutes(45);
        ctx.routines().save(gym);

        assertThat(ctx.tasks().tasksForDay(TODAY)).singleElement().satisfies(t -> {
            assertThat(t.getId()).isEqualTo(today.getId());
            assertThat(t.getTrackedMinutes()).isEqualTo(15);
        });
        // El resto de días sí se regeneran con la duración nueva
        assertThat(ctx.tasks().tasksForDay(TODAY.plusDays(1)).get(0).getEstimatedMinutes()).isEqualTo(45);
    }

    @Test
    void startWorkingRejectsClosedTasks() {
        Task report = task("Informe", Area.TRABAJO);
        ctx.tasks().toggleDone(report.getId());
        assertThatThrownBy(() -> ctx.tasks().startWorking(report.getId())).isInstanceOf(ValidationException.class);
    }
}
