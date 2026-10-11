package com.alejandro.synclife.service;

import com.alejandro.synclife.dao.TaskDao;
import com.alejandro.synclife.dao.TimeEntryDao;
import com.alejandro.synclife.dao.WeeklyReviewDao;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.entity.WeeklyPriority;
import com.alejandro.synclife.model.entity.WeeklyReview;
import com.alejandro.synclife.model.enums.TaskStatus;
import com.alejandro.synclife.service.GoalService.GoalProgress;
import org.hibernate.SessionFactory;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Revisión semanal guiada: repasar cómo fue una semana y planificar la siguiente (pendientes, días,
 * hasta tres prioridades y notas). Cada revisión se identifica por el lunes de la semana que planifica.
 */
public class WeeklyReviewService {

    public static final int MAX_PRIORITIES = 3;
    public static final int MAX_NOTES = 4000;

    /**
     * Cómo fue la semana que empieza en {@code reviewedMonday}: lo completado esa semana (tareas puntuales),
     * lo que queda pendiente de esa semana o antes, el tiempo registrado, el cumplimiento de las rutinas
     * (días ya vividos) y los objetivos en curso.
     */
    public record Summary(LocalDate reviewedMonday, List<Task> completed, List<Task> pending, long minutesTracked,
                          long routineDone, long routineTotal, List<GoalProgress> goals) {
        public LocalDate reviewedSunday() {
            return reviewedMonday.plusDays(6);
        }

        public LocalDate plannedMonday() {
            return reviewedMonday.plusWeeks(1);
        }
    }

    private final SessionFactory sessionFactory;
    private final WeeklyReviewDao reviewDao;
    private final TaskDao taskDao;
    private final TimeEntryDao timeEntryDao;
    private final GoalService goals;
    private final Clock clock;

    public WeeklyReviewService(SessionFactory sessionFactory, WeeklyReviewDao reviewDao, TaskDao taskDao,
                               TimeEntryDao timeEntryDao, GoalService goals, Clock clock) {
        this.sessionFactory = sessionFactory;
        this.reviewDao = reviewDao;
        this.taskDao = taskDao;
        this.timeEntryDao = timeEntryDao;
        this.goals = goals;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /**
     * Qué semana conviene planificar hoy: de lunes a miércoles, la actual (aún se está a tiempo);
     * de jueves a domingo, la siguiente.
     */
    public LocalDate defaultPlannedMonday() {
        LocalDate today = today();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        return today.getDayOfWeek().getValue() <= DayOfWeek.WEDNESDAY.getValue() ? monday : monday.plusWeeks(1);
    }

    public Summary summarize(LocalDate reviewedMonday) {
        requireMonday(reviewedMonday);
        LocalDate sunday = reviewedMonday.plusDays(6);
        LocalDate lastLived = sunday.isAfter(today()) ? today() : sunday;
        Summary partial = sessionFactory.fromTransaction(s -> {
            List<Task> completed = taskDao.findCompletedBetween(s, reviewedMonday.atStartOfDay(),
                            sunday.plusDays(1).atStartOfDay()).stream()
                    .filter(t -> !t.isRoutineInstance()).toList();
            // Pendiente = puntual, abierta y programada antes de la semana siguiente
            List<Task> pending = taskDao.findCarryOver(s, sunday.plusDays(1));
            long minutes = timeEntryDao.minutesByArea(s, reviewedMonday.atStartOfDay(), sunday.plusDays(1).atStartOfDay())
                    .values().stream().mapToLong(Long::longValue).sum();
            long routineDone = 0;
            long routineTotal = 0;
            for (Task task : taskDao.findBetween(s, reviewedMonday, sunday)) {
                if (task.isRoutineInstance() && !task.getScheduledDate().isAfter(lastLived)) {
                    routineTotal++;
                    if (task.getStatus() == TaskStatus.COMPLETADA) {
                        routineDone++;
                    }
                }
            }
            return new Summary(reviewedMonday, completed, pending, minutes, routineDone, routineTotal, List.of());
        });
        return new Summary(reviewedMonday, partial.completed(), partial.pending(), partial.minutesTracked(),
                partial.routineDone(), partial.routineTotal(), goals.findAll(false));
    }

    public Optional<WeeklyReview> find(LocalDate plannedMonday) {
        return sessionFactory.fromTransaction(s -> reviewDao.findByWeek(s, plannedMonday));
    }

    /** Revisiones guardadas, de la más reciente a la más antigua. */
    public List<WeeklyReview> history(int limit) {
        return sessionFactory.fromTransaction(s -> reviewDao.findRecent(s, limit));
    }

    /** Las prioridades de la semana que empieza en {@code plannedMonday}, en orden (vacío si no hay revisión). */
    public List<Task> prioritiesFor(LocalDate plannedMonday) {
        return sessionFactory.fromTransaction(s -> reviewDao.findPriorityTasks(s, plannedMonday));
    }

    /** Tareas que pueden ser prioridad de la semana: puntuales y abiertas, de esa semana o sin fecha. */
    public List<Task> priorityCandidates(LocalDate plannedMonday) {
        return sessionFactory.fromTransaction(s -> {
            List<Task> candidates = new ArrayList<>();
            for (Task task : taskDao.findBetween(s, plannedMonday, plannedMonday.plusDays(6))) {
                if (task.isOpen() && !task.isRoutineInstance()) {
                    candidates.add(task);
                }
            }
            candidates.addAll(taskDao.findUnscheduled(s).stream().filter(t -> !t.isRoutineInstance()).toList());
            return candidates;
        });
    }

    /**
     * Guarda (o actualiza) la revisión de la semana que empieza en {@code plannedMonday}: notas, foto de la
     * semana anterior y prioridades en el orden dado (como mucho {@value #MAX_PRIORITIES}, sin repetir y sin
     * tareas de rutinas). Las prioridades anteriores de esa semana se sustituyen.
     */
    public WeeklyReview save(LocalDate plannedMonday, String notes, List<Long> priorityTaskIds) {
        requireMonday(plannedMonday);
        List<Long> ids = priorityTaskIds.stream().filter(Objects::nonNull).toList();
        if (ids.size() > MAX_PRIORITIES) {
            throw new ValidationException("Elige como mucho " + MAX_PRIORITIES + " prioridades para la semana.");
        }
        Set<Long> distinct = new HashSet<>(ids);
        if (distinct.size() != ids.size()) {
            throw new ValidationException("La misma tarea no puede ser dos prioridades.");
        }
        String text = notes == null || notes.isBlank() ? null : notes.strip();
        if (text != null && text.length() > MAX_NOTES) {
            throw new ValidationException("Las notas no pueden superar " + MAX_NOTES + " caracteres.");
        }
        Summary previous = summarize(plannedMonday.minusWeeks(1));

        return sessionFactory.fromTransaction(s -> {
            List<Task> tasks = new ArrayList<>();
            for (Long id : ids) {
                Task task = s.find(Task.class, id);
                if (task == null) {
                    throw new ValidationException("Una de las tareas elegidas ya no existe.");
                }
                if (task.isRoutineInstance()) {
                    throw new ValidationException("Las tareas de una rutina no pueden ser prioridades de la semana.");
                }
                tasks.add(task);
            }
            WeeklyReview review = reviewDao.findByWeek(s, plannedMonday).orElse(null);
            if (review == null) {
                review = new WeeklyReview(plannedMonday);
                s.persist(review);
            } else {
                reviewDao.deletePriorities(s, review.getId());
            }
            review.setNotes(text);
            review.setCompletedCount(previous.completed().size());
            review.setPendingCount(previous.pending().size());
            for (int i = 0; i < tasks.size(); i++) {
                s.persist(new WeeklyPriority(review, tasks.get(i), i + 1));
            }
            return review;
        });
    }

    private static void requireMonday(LocalDate date) {
        if (date == null || date.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new ValidationException("La semana de una revisión empieza en lunes.");
        }
    }
}
