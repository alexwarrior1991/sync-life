package com.alejandro.synclife.service;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.TestSupport;
import com.alejandro.synclife.model.DaysOfWeek;
import com.alejandro.synclife.model.entity.Goal;
import com.alejandro.synclife.model.entity.Routine;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.entity.WeeklyReview;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.TimeSource;
import com.alejandro.synclife.service.WeeklyReviewService.Summary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static com.alejandro.synclife.TestSupport.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeeklyReviewServiceTest {

    /** TODAY es el lunes 12 de octubre de 2026. */
    private static final LocalDate LAST_MONDAY = TODAY.minusWeeks(1);
    private static final LocalDate NEXT_MONDAY = TODAY.plusWeeks(1);

    @TempDir
    Path dir;
    AppContext ctx;

    @AfterEach
    void tearDown() {
        if (ctx != null) {
            ctx.close();
        }
    }

    private AppContext open(LocalDate today) {
        if (ctx != null) {
            ctx.close();
        }
        ctx = TestSupport.context(dir, today);
        return ctx;
    }

    private Task task(String title, LocalDate day) {
        Task task = new Task(title, Area.TRABAJO);
        task.setScheduledDate(day);
        return ctx.tasks().save(task);
    }

    @Test
    void earlyInTheWeekItPlansThisWeekAndFromThursdayTheNextOne() {
        assertThat(open(TODAY).reviews().defaultPlannedMonday()).isEqualTo(TODAY);                  // lunes
        assertThat(open(TODAY.plusDays(2)).reviews().defaultPlannedMonday()).isEqualTo(TODAY);      // miércoles
        assertThat(open(TODAY.plusDays(3)).reviews().defaultPlannedMonday()).isEqualTo(NEXT_MONDAY); // jueves
        assertThat(open(TODAY.plusDays(6)).reviews().defaultPlannedMonday()).isEqualTo(NEXT_MONDAY); // domingo
    }

    @Test
    void summaryCoversWhatWasDonePendingAndTracked() {
        // Revisamos la semana actual desde el domingo 18
        open(TODAY);
        Task done = task("Informe", TODAY.plusDays(1));
        Task pending = task("Presupuesto", TODAY.plusDays(2));
        Task older = task("De la semana pasada", TODAY.minusDays(3));
        Task nextWeek = task("De la semana que viene", NEXT_MONDAY);
        ctx.timeTracking().record(done.getId(), TODAY.atTime(10, 0), TODAY.atTime(10, 25), 25, TimeSource.POMODORO);
        ctx.tasks().toggleDone(done.getId());
        ctx.routines().save(new Routine("Daily", Area.TRABAJO, DaysOfWeek.WEEKDAYS, TODAY));
        Goal goal = ctx.goals().save(new Goal("Lanzar la 2.0", Area.TRABAJO));
        ctx.goals().link(goal.getId(), List.of(pending.getId()));

        open(TODAY.plusDays(6));
        Summary summary = ctx.reviews().summarize(TODAY);

        assertThat(summary.completed()).extracting(Task::getTitle).containsExactly("Informe");
        assertThat(summary.pending()).extracting(Task::getTitle)
                .containsExactly("De la semana pasada", "Presupuesto");
        assertThat(summary.pending()).extracting(Task::getId).doesNotContain(nextWeek.getId());
        assertThat(summary.minutesTracked()).isEqualTo(25);
        assertThat(summary.routineTotal()).isEqualTo(5); // lunes a viernes, ninguno hecho
        assertThat(summary.routineDone()).isZero();
        assertThat(summary.goals()).singleElement().satisfies(p -> assertThat(p.total()).isEqualTo(1));
        assertThat(summary.plannedMonday()).isEqualTo(NEXT_MONDAY);
        assertThat(older.getId()).isNotNull();
    }

    @Test
    void savingCreatesOneReviewPerWeekAndUpdatesIt() {
        open(TODAY.plusDays(5));
        Task a = task("A", NEXT_MONDAY);
        Task b = task("B", NEXT_MONDAY.plusDays(1));
        Task c = task("C", null);

        WeeklyReview first = ctx.reviews().save(NEXT_MONDAY, "  Primera versión  ", List.of(a.getId(), b.getId()));
        WeeklyReview second = ctx.reviews().save(NEXT_MONDAY, "Segunda", List.of(c.getId(), a.getId()));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(ctx.reviews().find(NEXT_MONDAY).orElseThrow().getNotes()).isEqualTo("Segunda");
        assertThat(ctx.reviews().prioritiesFor(NEXT_MONDAY)).extracting(Task::getTitle).containsExactly("C", "A");
        assertThat(ctx.reviews().history(10)).hasSize(1);
        assertThat(TestSupport.single(ctx, "select count(*) from weekly_priority")).isEqualTo("2");
    }

    @Test
    void replacingPrioritiesInTheSameSlotsDoesNotClash() {
        open(TODAY);
        Task a = task("A", TODAY);
        Task b = task("B", TODAY);
        ctx.reviews().save(TODAY, null, List.of(a.getId(), b.getId()));
        // Mismos huecos (1 y 2) con las tareas cambiadas de sitio: chocaría si no se borrasen antes
        ctx.reviews().save(TODAY, null, List.of(b.getId(), a.getId()));
        assertThat(ctx.reviews().prioritiesFor(TODAY)).extracting(Task::getTitle).containsExactly("B", "A");
        ctx.reviews().save(TODAY, null, List.of());
        assertThat(ctx.reviews().prioritiesFor(TODAY)).isEmpty();
    }

    @Test
    void prioritiesAreLimitedDistinctAndNotFromRoutines() {
        open(TODAY);
        Task a = task("A", TODAY);
        Task b = task("B", TODAY);
        Task c = task("C", TODAY);
        Task d = task("D", TODAY);
        ctx.routines().save(new Routine("Daily", Area.TRABAJO, DaysOfWeek.ALL, TODAY));
        Task instance = ctx.tasks().tasksForDay(TODAY).stream().filter(Task::isRoutineInstance).findFirst().orElseThrow();

        assertThatThrownBy(() -> ctx.reviews().save(TODAY, null, List.of(a.getId(), b.getId(), c.getId(), d.getId())))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> ctx.reviews().save(TODAY, null, List.of(a.getId(), a.getId())))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> ctx.reviews().save(TODAY, null, List.of(instance.getId())))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> ctx.reviews().save(TODAY.plusDays(1), null, List.of()))
                .isInstanceOf(ValidationException.class);
        assertThat(ctx.reviews().history(10)).isEmpty();
    }

    @Test
    void theReviewKeepsASnapshotOfThePreviousWeek() {
        open(LAST_MONDAY);
        Task done = task("Hecha la semana pasada", LAST_MONDAY);
        ctx.tasks().toggleDone(done.getId());
        task("Pendiente de la semana pasada", LAST_MONDAY.plusDays(2));

        open(TODAY);
        WeeklyReview review = ctx.reviews().save(TODAY, "Notas", List.of());

        assertThat(review.getCompletedCount()).isEqualTo(1);
        assertThat(review.getPendingCount()).isEqualTo(1);
    }

    @Test
    void candidatesAreOpenOrdinaryTasksOfTheWeekOrUnscheduled() {
        open(TODAY);
        task("Esta semana", TODAY.plusDays(3));
        task("Sin fecha", null);
        task("La semana que viene", NEXT_MONDAY);
        Task done = task("Hecha", TODAY);
        ctx.tasks().toggleDone(done.getId());
        ctx.routines().save(new Routine("Daily", Area.TRABAJO, DaysOfWeek.ALL, TODAY));

        assertThat(ctx.reviews().priorityCandidates(TODAY)).extracting(Task::getTitle)
                .containsExactly("Esta semana", "Sin fecha");
    }

    @Test
    void deletingAPriorityTaskRemovesItFromTheReview() {
        open(TODAY);
        Task a = task("A", TODAY);
        Task b = task("B", TODAY);
        ctx.reviews().save(TODAY, null, List.of(a.getId(), b.getId()));

        ctx.tasks().delete(a.getId());

        assertThat(ctx.reviews().prioritiesFor(TODAY)).extracting(Task::getTitle).containsExactly("B");
    }
}
