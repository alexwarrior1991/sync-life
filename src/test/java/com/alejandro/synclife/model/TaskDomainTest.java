package com.alejandro.synclife.model;

import com.alejandro.synclife.model.entity.Routine;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.Priority;
import com.alejandro.synclife.model.enums.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class TaskDomainTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 18, 30, 15, 999);

    @Test
    void markDoneAndReopenKeepCompletedAtConsistent() {
        Task task = new Task("Informe", Area.TRABAJO);
        task.markDone(NOW);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETADA);
        assertThat(task.getCompletedAt()).isEqualTo(NOW.withNano(0));
        assertThat(task.isOpen()).isFalse();

        task.reopen();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDIENTE);
        assertThat(task.getCompletedAt()).isNull();
    }

    @Test
    void changeStatusClearsCompletedAtWhenLeavingCompleted() {
        Task task = new Task("Informe", Area.TRABAJO);
        task.changeStatus(TaskStatus.COMPLETADA, NOW);
        assertThat(task.getCompletedAt()).isNotNull();
        task.changeStatus(TaskStatus.EN_PROGRESO, NOW);
        assertThat(task.getCompletedAt()).isNull();
        assertThat(task.isOpen()).isTrue();
    }

    @Test
    void overdueOnlyWhenOpenAndDeadlinePassed() {
        LocalDate today = LocalDate.of(2026, 10, 12);
        Task task = new Task("Entrega", Area.TRABAJO);
        task.setDueDate(today.minusDays(1));
        assertThat(task.isOverdue(today)).isTrue();
        task.setDueDate(today);
        assertThat(task.isOverdue(today)).isFalse();
        task.setDueDate(today.minusDays(1));
        task.markDone(NOW);
        assertThat(task.isOverdue(today)).isFalse();
    }

    @Test
    void routineCreatesInstanceWithItsData() {
        Routine routine = new Routine("Gimnasio", Area.DEPORTE, DaysOfWeek.ALL, LocalDate.of(2026, 10, 1));
        routine.setStartTime(LocalTime.of(19, 0));
        routine.setDurationMinutes(60);
        routine.setPriority(Priority.ALTA);

        Task task = routine.newInstanceFor(LocalDate.of(2026, 10, 12));
        assertThat(task.getTitle()).isEqualTo("Gimnasio");
        assertThat(task.getArea()).isEqualTo(Area.DEPORTE);
        assertThat(task.getScheduledDate()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(task.getStartTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(task.getEstimatedMinutes()).isEqualTo(60);
        assertThat(task.getPriority()).isEqualTo(Priority.ALTA);
        assertThat(task.isRoutineInstance()).isTrue();
    }
}
