package com.alejandro.synclife.service;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.TestSupport;
import com.alejandro.synclife.model.DaysOfWeek;
import com.alejandro.synclife.model.TaskFilter;
import com.alejandro.synclife.model.entity.Category;
import com.alejandro.synclife.model.entity.Routine;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.Priority;
import com.alejandro.synclife.model.enums.TaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static com.alejandro.synclife.TestSupport.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskServiceTest {

    @TempDir
    Path dir;
    AppContext ctx;
    TaskService tasks;

    @BeforeEach
    void setUp() {
        ctx = TestSupport.context(dir);
        tasks = ctx.tasks();
    }

    @AfterEach
    void tearDown() {
        ctx.close();
    }

    private Task task(String title, Area area, LocalDate day, LocalTime time, Priority priority) {
        Task t = new Task(title, area);
        t.setScheduledDate(day);
        t.setStartTime(time);
        t.setPriority(priority);
        return tasks.save(t);
    }

    private Category category(String name) {
        return ctx.categories().findAll(true).stream()
                .filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void dayIsOrderedByTimeThenPriority() {
        task("Sin hora baja", Area.VIDA, TODAY, null, Priority.BAJA);
        task("Diez", Area.TRABAJO, TODAY, LocalTime.of(10, 0), Priority.MEDIA);
        task("Sin hora urgente", Area.TRABAJO, TODAY, null, Priority.URGENTE);
        task("Ocho", Area.DEPORTE, TODAY, LocalTime.of(8, 0), Priority.BAJA);
        task("Mañana", Area.TRABAJO, TODAY.plusDays(1), null, Priority.MEDIA);

        assertThat(tasks.tasksForDay(TODAY)).extracting(Task::getTitle)
                .containsExactly("Ocho", "Diez", "Sin hora urgente", "Sin hora baja");
    }

    @Test
    void returnedTasksHaveTheirCategoryLoaded() {
        Task t = new Task("Daily", Area.TRABAJO);
        t.setScheduledDate(TODAY);
        t.setCategory(category("Reuniones"));
        tasks.save(t);

        Task loaded = tasks.tasksForDay(TODAY).get(0);
        // La sesión ya está cerrada: no debe lanzar LazyInitializationException
        assertThat(loaded.getCategory().getName()).isEqualTo("Reuniones");
        assertThat(loaded.getCategory().getArea()).isEqualTo(Area.TRABAJO);
    }

    @Test
    void choosingACategoryAlignsTheArea() {
        Task t = new Task("Correr 5 km", Area.TRABAJO);
        t.setCategory(category("Correr"));
        assertThat(tasks.save(t).getArea()).isEqualTo(Area.DEPORTE);
    }

    @Test
    void editingADetachedTaskPersistsChanges() {
        Task t = task("Borrador", Area.TRABAJO, TODAY, null, Priority.MEDIA);
        t.setTitle("Informe final");
        t.setPriority(Priority.ALTA);
        t.setDueDate(TODAY.plusDays(3));
        t.setCategory(category("Proyectos"));
        tasks.save(t);

        Task reloaded = tasks.findById(t.getId()).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Informe final");
        assertThat(reloaded.getPriority()).isEqualTo(Priority.ALTA);
        assertThat(reloaded.getDueDate()).isEqualTo(TODAY.plusDays(3));
        assertThat(reloaded.getCategory().getName()).isEqualTo("Proyectos");
    }

    @Test
    void validatesTitle() {
        assertThatThrownBy(() -> tasks.save(new Task("   ", Area.VIDA)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("título");
    }

    @Test
    void toggleDoneSetsAndClearsCompletedAt() {
        Task t = task("Llamar al banco", Area.VIDA, TODAY, null, Priority.MEDIA);

        Task done = tasks.toggleDone(t.getId());
        assertThat(done.getStatus()).isEqualTo(TaskStatus.COMPLETADA);
        assertThat(done.getCompletedAt()).isEqualTo(TODAY.atTime(9, 0));

        Task reopened = tasks.toggleDone(t.getId());
        assertThat(reopened.getStatus()).isEqualTo(TaskStatus.PENDIENTE);
        assertThat(reopened.getCompletedAt()).isNull();
    }

    @Test
    void savingWithCompletedStatusStampsCompletedAt() {
        Task t = new Task("Hecho desde el editor", Area.VIDA);
        t.setStatus(TaskStatus.COMPLETADA);
        assertThat(tasks.save(t).getCompletedAt()).isNotNull();
    }

    @Test
    void carryOverListsOpenPastTasksOnly() {
        task("Pendiente de ayer", Area.TRABAJO, TODAY.minusDays(1), null, Priority.MEDIA);
        Task done = task("Hecha ayer", Area.TRABAJO, TODAY.minusDays(1), null, Priority.MEDIA);
        tasks.toggleDone(done.getId());
        task("De hoy", Area.TRABAJO, TODAY, null, Priority.MEDIA);

        assertThat(tasks.carryOver()).extracting(Task::getTitle).containsExactly("Pendiente de ayer");
    }

    @Test
    void rescheduleAllMovesTasksToADay() {
        Task a = task("A", Area.TRABAJO, TODAY.minusDays(2), null, Priority.MEDIA);
        Task b = task("B", Area.TRABAJO, TODAY.minusDays(1), null, Priority.MEDIA);
        assertThat(tasks.rescheduleAll(List.of(a.getId(), b.getId()), TODAY)).isEqualTo(2);
        assertThat(tasks.carryOver()).isEmpty();
        assertThat(tasks.tasksForDay(TODAY)).hasSize(2);
    }

    @Test
    void dueSoonIncludesOverdueAndUpcomingDeadlines() {
        Task overdue = new Task("Vencida", Area.TRABAJO);
        overdue.setDueDate(TODAY.minusDays(1));
        tasks.save(overdue);
        Task soon = new Task("Pronto", Area.TRABAJO);
        soon.setDueDate(TODAY.plusDays(2));
        tasks.save(soon);
        Task later = new Task("Lejos", Area.TRABAJO);
        later.setDueDate(TODAY.plusDays(30));
        tasks.save(later);

        assertThat(tasks.dueSoon(3)).extracting(Task::getTitle).containsExactly("Vencida", "Pronto");
    }

    @Test
    void weekGroupsTasksByDay() {
        task("Lunes", Area.TRABAJO, TODAY, null, Priority.MEDIA);
        task("Domingo", Area.VIDA, TODAY.plusDays(6), null, Priority.MEDIA);
        task("Otra semana", Area.VIDA, TODAY.plusDays(7), null, Priority.MEDIA);

        Map<LocalDate, List<Task>> week = tasks.tasksForWeek(TODAY);
        assertThat(week).hasSize(7);
        assertThat(week.get(TODAY)).extracting(Task::getTitle).containsExactly("Lunes");
        assertThat(week.get(TODAY.plusDays(6))).extracting(Task::getTitle).containsExactly("Domingo");
    }

    @Test
    void searchCombinesFiltersAndAccentInsensitiveText() {
        Task meeting = new Task("Reunión semanal", Area.TRABAJO);
        meeting.setScheduledDate(TODAY);
        meeting.setPriority(Priority.ALTA);
        tasks.save(meeting);
        Task gym = task("Gimnasio", Area.DEPORTE, TODAY, null, Priority.MEDIA);
        tasks.toggleDone(gym.getId());
        tasks.save(new Task("Idea sin fecha", Area.VIDA));

        assertThat(tasks.search(TaskFilter.all().text("reunion"))).extracting(Task::getTitle)
                .containsExactly("Reunión semanal");
        assertThat(tasks.search(TaskFilter.all().area(Area.DEPORTE))).extracting(Task::getTitle)
                .containsExactly("Gimnasio");
        assertThat(tasks.search(TaskFilter.all().statuses(TaskStatus.OPEN))).hasSize(2);
        assertThat(tasks.search(TaskFilter.all().statuses(EnumSet.of(TaskStatus.COMPLETADA))))
                .extracting(Task::getTitle).containsExactly("Gimnasio");
        assertThat(tasks.search(TaskFilter.all().unscheduledOnly(true))).extracting(Task::getTitle)
                .containsExactly("Idea sin fecha");
        assertThat(tasks.search(TaskFilter.all().priority(Priority.ALTA))).hasSize(1);
        assertThat(tasks.search(TaskFilter.all().scheduledBetween(TODAY, TODAY))).hasSize(2);
    }

    @Test
    void routineInstancesCannotBeDeletedOrMovedButCanBeSkipped() {
        Routine routine = new Routine("Revisar correo", Area.TRABAJO, DaysOfWeek.ALL, TODAY);
        ctx.routines().save(routine);
        Task instance = tasks.tasksForDay(TODAY).get(0);
        assertThat(instance.isRoutineInstance()).isTrue();
        assertThat(instance.getRoutine().getName()).isEqualTo("Revisar correo");

        assertThatThrownBy(() -> tasks.delete(instance.getId())).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> tasks.reschedule(instance.getId(), TODAY.plusDays(1)))
                .isInstanceOf(ValidationException.class);
        instance.setScheduledDate(TODAY.plusDays(1));
        assertThatThrownBy(() -> tasks.save(instance)).isInstanceOf(ValidationException.class);

        assertThat(tasks.skip(instance.getId()).getStatus()).isEqualTo(TaskStatus.CANCELADA);
        assertThat(tasks.search(TaskFilter.all().includeRoutineInstances(false))).isEmpty();
    }

    @Test
    void deletesOrdinaryTasks() {
        Task t = task("Temporal", Area.VIDA, TODAY, null, Priority.BAJA);
        tasks.delete(t.getId());
        assertThat(tasks.findById(t.getId())).isEmpty();
    }
}
