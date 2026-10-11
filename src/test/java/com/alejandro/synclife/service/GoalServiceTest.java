package com.alejandro.synclife.service;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.TestSupport;
import com.alejandro.synclife.model.DaysOfWeek;
import com.alejandro.synclife.model.entity.Goal;
import com.alejandro.synclife.model.entity.Routine;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.GoalStatus;
import com.alejandro.synclife.service.GoalService.GoalProgress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static com.alejandro.synclife.TestSupport.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoalServiceTest {

    @TempDir
    Path dir;
    AppContext ctx;
    GoalService goals;

    @BeforeEach
    void setUp() {
        ctx = TestSupport.context(dir);
        goals = ctx.goals();
    }

    @AfterEach
    void tearDown() {
        ctx.close();
    }

    private Goal goal(String title, Area area) {
        return goals.save(new Goal(title, area));
    }

    private Task task(String title, Area area) {
        Task task = new Task(title, area);
        task.setScheduledDate(TODAY);
        return ctx.tasks().save(task);
    }

    @Test
    void progressCountsCompletedOverLinkedTasksIgnoringCancelled() {
        Goal tenK = goal("Correr una 10K", Area.DEPORTE);
        Task five = task("Correr 5 km", Area.DEPORTE);
        Task seven = task("Correr 7 km", Area.DEPORTE);
        Task shoes = task("Comprar zapatillas", Area.VIDA); // de otra área: se permite
        Task dropped = task("Apuntarse a un club", Area.DEPORTE);
        goals.link(tenK.getId(), List.of(five.getId(), seven.getId(), shoes.getId(), dropped.getId()));
        ctx.tasks().toggleDone(five.getId());
        ctx.tasks().toggleDone(shoes.getId());
        ctx.tasks().skip(dropped.getId());

        GoalProgress progress = goals.progress(tenK.getId()).orElseThrow();

        assertThat(progress.done()).isEqualTo(2);
        assertThat(progress.total()).isEqualTo(3);
        assertThat(progress.open()).isEqualTo(1);
        assertThat(progress.rate()).isEqualTo(2.0 / 3);
    }

    @Test
    void tasksOfAGoalComeWithTheirGoalLoadedAndOpenFirst() {
        Goal release = goal("Lanzar la 2.0", Area.TRABAJO);
        Task done = task("Hecha", Area.TRABAJO);
        Task open = task("Abierta", Area.TRABAJO);
        goals.link(release.getId(), List.of(done.getId(), open.getId()));
        ctx.tasks().toggleDone(done.getId());

        List<Task> tasks = goals.tasksOf(release.getId());

        assertThat(tasks).extracting(Task::getTitle).containsExactly("Abierta", "Hecha");
        // La sesión ya está cerrada: no debe lanzar LazyInitializationException
        assertThat(tasks.get(0).getGoal().getTitle()).isEqualTo("Lanzar la 2.0");
        assertThat(ctx.tasks().tasksForDay(TODAY).get(0).getGoal().getTitle()).isEqualTo("Lanzar la 2.0");
    }

    @Test
    void closedGoalsAreHiddenUnlessAskedAndAchievingStampsTheDate() {
        Goal open = goal("En curso", Area.TRABAJO);
        Goal achieved = goal("Conseguido", Area.TRABAJO);
        Goal abandoned = goal("Abandonado", Area.VIDA);

        Goal done = goals.setStatus(achieved.getId(), GoalStatus.CONSEGUIDO);
        goals.setStatus(abandoned.getId(), GoalStatus.ABANDONADO);

        assertThat(done.getCompletedAt()).isEqualTo(TODAY.atTime(9, 0));
        assertThat(goals.findAll(false)).extracting(p -> p.goal().getTitle()).containsExactly("En curso");
        assertThat(goals.findAll(true)).extracting(p -> p.goal().getTitle())
                .first().isEqualTo("En curso");
        assertThat(goals.findAll(true)).hasSize(3);
        assertThat(goals.setStatus(achieved.getId(), GoalStatus.EN_CURSO).getCompletedAt()).isNull();
        assertThat(goals.findOpen()).extracting(Goal::getTitle).containsExactlyInAnyOrder("En curso", "Conseguido");
        assertThat(open.isOpen()).isTrue();
    }

    @Test
    void linkableTasksAreOpenOrdinaryAndWithoutGoal() {
        Goal goal = goal("Objetivo", Area.TRABAJO);
        Task free = task("Libre", Area.TRABAJO);
        Task linked = task("Ya vinculada", Area.TRABAJO);
        Task closed = task("Hecha", Area.TRABAJO);
        goals.link(goal.getId(), List.of(linked.getId()));
        ctx.tasks().toggleDone(closed.getId());
        ctx.routines().save(new Routine("Daily", Area.TRABAJO, DaysOfWeek.ALL, TODAY));

        assertThat(goals.linkable()).extracting(Task::getTitle).containsExactly("Libre");
        assertThat(free.getGoal()).isNull();
    }

    @Test
    void routineInstancesAreNotLinked() {
        Goal goal = goal("Objetivo", Area.TRABAJO);
        ctx.routines().save(new Routine("Daily", Area.TRABAJO, DaysOfWeek.ALL, TODAY));
        Task instance = ctx.tasks().tasksForDay(TODAY).get(0);

        assertThat(goals.link(goal.getId(), List.of(instance.getId()))).isZero();
        instance.setGoal(goal);
        assertThatThrownBy(() -> ctx.tasks().save(instance)).isInstanceOf(ValidationException.class);
    }

    @Test
    void choosingAGoalInTheEditorAndUnlinking() {
        Goal goal = goal("Objetivo", Area.TRABAJO);
        Task task = new Task("Desde el editor", Area.VIDA);
        task.setGoal(goal);
        Task saved = ctx.tasks().save(task);
        assertThat(saved.getGoal().getTitle()).isEqualTo("Objetivo");

        goals.unlink(saved.getId());
        assertThat(ctx.tasks().findById(saved.getId()).orElseThrow().getGoal()).isNull();
    }

    @Test
    void validatesTitleAndArea() {
        assertThatThrownBy(() -> goals.save(new Goal("  ", Area.TRABAJO))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> goals.save(new Goal("x".repeat(121), Area.TRABAJO)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> goals.save(new Goal("Sin área", null))).isInstanceOf(ValidationException.class);
    }
}
