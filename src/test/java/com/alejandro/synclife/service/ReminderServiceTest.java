package com.alejandro.synclife.service;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.TestSupport;
import com.alejandro.synclife.model.DaysOfWeek;
import com.alejandro.synclife.model.entity.Routine;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.service.ReminderService.Due;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static com.alejandro.synclife.TestSupport.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReminderServiceTest {

    @TempDir
    Path dir;
    AppContext ctx;

    @AfterEach
    void tearDown() {
        if (ctx != null) {
            ctx.close();
        }
    }

    /** Abre (o reabre) la base de datos con el reloj en {@code now}. */
    private AppContext at(LocalDateTime now) {
        if (ctx != null) {
            ctx.close();
        }
        ctx = TestSupport.contextAt(dir, now);
        return ctx;
    }

    private Task task(String title, LocalDateTime remindAt) {
        Task task = new Task(title, Area.TRABAJO);
        task.setScheduledDate(remindAt.toLocalDate());
        task.setStartTime(remindAt.toLocalTime());
        task.setRemindAt(remindAt);
        return ctx.tasks().save(task);
    }

    @Test
    void aReminderIsDeliveredOnceWhenItIsDue() {
        at(TODAY.atTime(9, 0));
        task("Daily", TODAY.atTime(9, 30));

        assertThat(ctx.reminders().claimDue().isEmpty()).isTrue();

        at(TODAY.atTime(9, 30, 20));
        Due due = ctx.reminders().claimDue();
        assertThat(due.fresh()).extracting(Task::getTitle).containsExactly("Daily");
        assertThat(due.missed()).isEmpty();
        assertThat(ctx.reminders().claimDue().isEmpty()).isTrue(); // no se repite
    }

    @Test
    void closedTasksAreNotReminded() {
        at(TODAY.atTime(9, 0));
        Task done = task("Hecha", TODAY.atTime(10, 0));
        Task skipped = task("Cancelada", TODAY.atTime(10, 0));
        ctx.tasks().toggleDone(done.getId());
        ctx.tasks().skip(skipped.getId());

        at(TODAY.atTime(10, 5));
        assertThat(ctx.reminders().claimDue().isEmpty()).isTrue();
    }

    @Test
    void remindersOlderThanTwoHoursAreReportedAsMissed() {
        at(TODAY.atTime(8, 0));
        task("Antigua", TODAY.atTime(9, 0));
        task("Reciente", TODAY.atTime(11, 30));

        // La aplicación estuvo cerrada hasta las 12:00
        at(TODAY.atTime(12, 0));
        Due due = ctx.reminders().claimDue();

        assertThat(due.missed()).extracting(Task::getTitle).containsExactly("Antigua");
        assertThat(due.fresh()).extracting(Task::getTitle).containsExactly("Reciente");
    }

    @Test
    void snoozingRemindsAgainLater() {
        at(TODAY.atTime(10, 0));
        Task daily = task("Daily", TODAY.atTime(10, 0));
        assertThat(ctx.reminders().claimDue().fresh()).hasSize(1);

        Task snoozed = ctx.reminders().snooze(daily.getId(), Duration.ofMinutes(10));
        assertThat(snoozed.getRemindAt()).isEqualTo(TODAY.atTime(10, 10));
        assertThat(ctx.reminders().claimDue().isEmpty()).isTrue();

        at(TODAY.atTime(10, 10));
        assertThat(ctx.reminders().claimDue().fresh()).extracting(Task::getTitle).containsExactly("Daily");
        assertThatThrownBy(() -> ctx.reminders().snooze(daily.getId(), Duration.ZERO))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void savingFromAnOpenEditorDoesNotRearmADeliveredReminder() {
        at(TODAY.atTime(9, 0));
        Task task = task("Informe", TODAY.atTime(10, 0));
        Task editorCopy = ctx.tasks().findById(task.getId()).orElseThrow(); // el editor se abrió antes del aviso

        at(TODAY.atTime(10, 0));
        assertThat(ctx.reminders().claimDue().fresh()).hasSize(1);

        editorCopy.setTitle("Informe final");
        ctx.tasks().save(editorCopy);
        assertThat(ctx.reminders().claimDue().isEmpty()).isTrue();

        // Cambiar la hora del aviso sí lo vuelve a dejar pendiente
        editorCopy = ctx.tasks().findById(task.getId()).orElseThrow();
        editorCopy.setRemindAt(TODAY.atTime(10, 30));
        ctx.tasks().save(editorCopy);
        at(TODAY.atTime(10, 30));
        assertThat(ctx.reminders().claimDue().fresh()).hasSize(1);
    }

    @Test
    void aReminderSetInThePastIsNotFired() {
        at(TODAY.atTime(12, 0));
        task("Ya pasó", TODAY.atTime(9, 0));
        assertThat(ctx.reminders().claimDue().isEmpty()).isTrue();
    }

    @Test
    void reschedulingMovesTheReminderTheSameDays() {
        at(TODAY.atTime(9, 0));
        Task task = task("Informe", TODAY.atTime(10, 0));

        Task moved = ctx.tasks().reschedule(task.getId(), TODAY.plusDays(2));
        assertThat(moved.getRemindAt()).isEqualTo(TODAY.plusDays(2).atTime(10, 0));

        ctx.tasks().rescheduleAll(List.of(task.getId()), TODAY.plusDays(3));
        assertThat(ctx.tasks().findById(task.getId()).orElseThrow().getRemindAt())
                .isEqualTo(TODAY.plusDays(3).atTime(10, 0));

        assertThat(ctx.tasks().reschedule(task.getId(), null).getRemindAt()).isNull();
    }

    @Test
    void routineInstancesInheritTheReminderButNotForTimesAlreadyPast() {
        at(TODAY.atTime(12, 0));
        Routine daily = new Routine("Daily", Area.TRABAJO, DaysOfWeek.ALL, TODAY);
        daily.setStartTime(LocalTime.of(9, 30));
        daily.setRemindMinutesBefore(10);
        Routine gym = new Routine("Gimnasio", Area.DEPORTE, DaysOfWeek.ALL, TODAY);
        gym.setStartTime(LocalTime.of(19, 0));
        gym.setRemindMinutesBefore(30);
        ctx.routines().save(daily);
        ctx.routines().save(gym);

        Task tomorrow = ctx.tasks().tasksForDay(TODAY.plusDays(1)).get(0);
        assertThat(tomorrow.getRemindAt()).isEqualTo(TODAY.plusDays(1).atTime(9, 20));
        // El daily de hoy (aviso a las 9:20) ya pasó: no se avisa a las 12
        assertThat(ctx.reminders().claimDue().isEmpty()).isTrue();

        at(TODAY.atTime(18, 30));
        assertThat(ctx.reminders().claimDue().fresh()).extracting(Task::getTitle).containsExactly("Gimnasio");
    }

    @Test
    void routineReminderIsValidated() {
        at(TODAY.atTime(9, 0));
        Routine routine = new Routine("Daily", Area.TRABAJO, DaysOfWeek.ALL, TODAY);
        routine.setRemindMinutesBefore(24 * 60 + 1);
        assertThatThrownBy(() -> ctx.routines().save(routine)).isInstanceOf(ValidationException.class);
    }
}
