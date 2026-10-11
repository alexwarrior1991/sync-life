package com.alejandro.synclife.service;

import com.alejandro.synclife.dao.GoalDao;
import com.alejandro.synclife.dao.TaskDao;
import com.alejandro.synclife.model.entity.Goal;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.GoalStatus;
import org.hibernate.SessionFactory;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Objetivos a largo plazo y sus tareas vinculadas. Un objetivo puede tener tareas de cualquier área
 * (p. ej. "Correr una 10K" con una tarea de Vida "Comprar zapatillas").
 */
public class GoalService {

    /** Un objetivo con su avance: tareas completadas frente al total (sin contar las canceladas). */
    public record GoalProgress(Goal goal, long done, long total) {
        public double rate() {
            return total == 0 ? 0 : (double) done / total;
        }

        public long open() {
            return total - done;
        }
    }

    private final SessionFactory sessionFactory;
    private final GoalDao goalDao;
    private final TaskDao taskDao;
    private final Clock clock;

    public GoalService(SessionFactory sessionFactory, GoalDao goalDao, TaskDao taskDao, Clock clock) {
        this.sessionFactory = sessionFactory;
        this.goalDao = goalDao;
        this.taskDao = taskDao;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- consultas

    /** Objetivos con su avance; los cerrados (conseguidos o abandonados) solo si se piden. */
    public List<GoalProgress> findAll(boolean includeClosed) {
        return sessionFactory.fromTransaction(s -> {
            Map<Long, long[]> counts = taskDao.countByGoal(s);
            List<GoalProgress> result = new ArrayList<>();
            for (Goal goal : goalDao.findAll(s, includeClosed)) {
                long[] c = counts.getOrDefault(goal.getId(), new long[2]);
                result.add(new GoalProgress(goal, c[1], c[0]));
            }
            return result;
        });
    }

    /** Objetivos en curso (para elegir uno en el editor de tareas). */
    public List<Goal> findOpen() {
        return sessionFactory.fromTransaction(s -> goalDao.findAll(s, false));
    }

    public Optional<GoalProgress> progress(long goalId) {
        return findAll(true).stream().filter(p -> p.goal().getId() == goalId).findFirst();
    }

    /** Tareas del objetivo, primero las abiertas. */
    public List<Task> tasksOf(long goalId) {
        return sessionFactory.fromTransaction(s -> taskDao.findByGoal(s, goalId));
    }

    /** Tareas que se pueden vincular: puntuales, abiertas y sin objetivo. */
    public List<Task> linkable() {
        return sessionFactory.fromTransaction(taskDao::findLinkable);
    }

    // ---------------------------------------------------------------- cambios

    public Goal save(Goal goal) {
        String title = goal.getTitle() == null ? "" : goal.getTitle().trim();
        if (title.isEmpty()) {
            throw new ValidationException("El título del objetivo es obligatorio.");
        }
        if (title.length() > 120) {
            throw new ValidationException("El título del objetivo no puede superar 120 caracteres.");
        }
        if (goal.getArea() == null) {
            throw new ValidationException("Elige el área del objetivo.");
        }
        goal.setTitle(title);
        if (goal.getDescription() != null && goal.getDescription().isBlank()) {
            goal.setDescription(null);
        }
        if (goal.getStatus() == null) {
            goal.setStatus(GoalStatus.EN_CURSO);
        }
        stampCompletion(goal);
        return sessionFactory.fromTransaction(s -> goalDao.save(s, goal));
    }

    public Goal setStatus(long goalId, GoalStatus status) {
        return sessionFactory.fromTransaction(s -> {
            Goal goal = goalDao.findById(s, goalId)
                    .orElseThrow(() -> new ValidationException("El objetivo ya no existe."));
            goal.setStatus(status);
            stampCompletion(goal);
            return goal;
        });
    }

    /** Borra el objetivo; la base de datos deja sus tareas sin objetivo. */
    public void delete(long goalId) {
        sessionFactory.inTransaction(s -> goalDao.delete(s, goalId));
    }

    /** Vincula tareas al objetivo; las de rutinas se ignoran. Devuelve cuántas se vincularon. */
    public int link(long goalId, Collection<Long> taskIds) {
        return sessionFactory.fromTransaction(s -> {
            Goal goal = goalDao.findById(s, goalId)
                    .orElseThrow(() -> new ValidationException("El objetivo ya no existe."));
            int linked = 0;
            for (Long id : taskIds) {
                Task task = s.find(Task.class, id);
                if (task != null && !task.isRoutineInstance()) {
                    task.setGoal(goal);
                    linked++;
                }
            }
            return linked;
        });
    }

    public void unlink(long taskId) {
        sessionFactory.inTransaction(s -> {
            Task task = s.find(Task.class, taskId);
            if (task != null) {
                task.setGoal(null);
            }
        });
    }

    /** Conseguido ⇒ fecha de logro; cualquier otro estado la quita. */
    private void stampCompletion(Goal goal) {
        if (goal.getStatus() == GoalStatus.CONSEGUIDO) {
            if (goal.getCompletedAt() == null) {
                goal.setCompletedAt(LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
            }
        } else {
            goal.setCompletedAt(null);
        }
    }
}
