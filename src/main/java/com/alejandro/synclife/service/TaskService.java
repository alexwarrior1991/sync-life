package com.alejandro.synclife.service;

import com.alejandro.synclife.dao.TaskDao;
import com.alejandro.synclife.model.TaskFilter;
import com.alejandro.synclife.model.entity.Category;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.TaskStatus;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Casos de uso de tareas. Cada método público es una transacción y devuelve entidades
 * desconectadas con su categoría y rutina ya cargadas.
 */
public class TaskService {

    static final String ROUTINE_MOVE_ERROR =
            "Las tareas de una rutina no se pueden mover de día. Puedes omitirla o editar la rutina.";
    static final String ROUTINE_DELETE_ERROR =
            "Las tareas generadas por una rutina no se eliminan: usa «Omitir» o edita la rutina.";

    private final SessionFactory sessionFactory;
    private final TaskDao taskDao;
    private final Clock clock;

    public TaskService(SessionFactory sessionFactory, TaskDao taskDao, Clock clock) {
        this.sessionFactory = sessionFactory;
        this.taskDao = taskDao;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    // ---------------------------------------------------------------- consultas

    public Optional<Task> findById(long id) {
        return sessionFactory.fromTransaction(s -> taskDao.findByIdFetched(s, id));
    }

    public List<Task> tasksForDay(LocalDate day) {
        return sessionFactory.fromTransaction(s -> taskDao.findForDay(s, day));
    }

    public List<Task> tasksBetween(LocalDate from, LocalDate to) {
        return sessionFactory.fromTransaction(s -> taskDao.findBetween(s, from, to));
    }

    /** Las 7 listas de la semana que empieza en {@code monday}, en orden. */
    public Map<LocalDate, List<Task>> tasksForWeek(LocalDate monday) {
        Map<LocalDate, List<Task>> week = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            week.put(monday.plusDays(i), new ArrayList<>());
        }
        for (Task task : tasksBetween(monday, monday.plusDays(6))) {
            week.get(task.getScheduledDate()).add(task);
        }
        return week;
    }

    /** Tareas puntuales abiertas de días anteriores a hoy. */
    public List<Task> carryOver() {
        return sessionFactory.fromTransaction(s -> taskDao.findCarryOver(s, today()));
    }

    /** Tareas abiertas que vencen en los próximos {@code days} días (o ya vencidas). */
    public List<Task> dueSoon(int days) {
        return sessionFactory.fromTransaction(s -> taskDao.findDueUntil(s, today().plusDays(days)));
    }

    public List<Task> unscheduled() {
        return sessionFactory.fromTransaction(taskDao::findUnscheduled);
    }

    public List<Task> search(TaskFilter filter) {
        List<Task> tasks = sessionFactory.fromTransaction(s -> taskDao.search(s, filter));
        String text = filter.getText();
        if (text == null || text.isBlank()) {
            return tasks;
        }
        return tasks.stream()
                .filter(t -> TextSearch.matches(text, t.getTitle(), t.getDescription(),
                        t.getCategory() == null ? null : t.getCategory().getName()))
                .toList();
    }

    // ---------------------------------------------------------------- cambios

    public Task save(Task task) {
        validate(task);
        return sessionFactory.fromTransaction(s -> {
            if (!task.isNew()) {
                Task current = s.find(Task.class, task.getId());
                if (current == null) {
                    throw new ValidationException("La tarea ya no existe.");
                }
                if (current.isRoutineInstance()
                        && !Objects.equals(current.getScheduledDate(), task.getScheduledDate())) {
                    throw new ValidationException(ROUTINE_MOVE_ERROR);
                }
                task.setRoutine(current.getRoutine()); // el vínculo con la rutina no se edita desde la UI
            }
            attachCategory(s, task);
            if (task.getStatus() == TaskStatus.COMPLETADA) {
                if (task.getCompletedAt() == null) {
                    task.setCompletedAt(now().withNano(0));
                }
            } else {
                task.setCompletedAt(null);
            }
            return initialized(taskDao.save(s, task));
        });
    }

    public Task quickAdd(String title, Area area, LocalDate date, LocalTime time) {
        Task task = new Task(title, area);
        task.setScheduledDate(date);
        task.setStartTime(time);
        return save(task);
    }

    /** Completa la tarea si está abierta; la reabre si ya estaba hecha o cancelada. */
    public Task toggleDone(long id) {
        return update(id, task -> {
            if (task.isOpen()) {
                task.markDone(now());
            } else {
                task.reopen();
            }
        });
    }

    public Task setStatus(long id, TaskStatus status) {
        return update(id, task -> task.changeStatus(status, now()));
    }

    public Task reschedule(long id, LocalDate date) {
        return update(id, task -> {
            if (task.isRoutineInstance()) {
                throw new ValidationException(ROUTINE_MOVE_ERROR);
            }
            task.setScheduledDate(date);
        });
    }

    /** Mueve varias tareas puntuales a {@code date}; ignora las de rutinas. Devuelve cuántas movió. */
    public int rescheduleAll(Collection<Long> ids, LocalDate date) {
        return sessionFactory.fromTransaction(s -> {
            int moved = 0;
            for (Long id : ids) {
                Task task = s.find(Task.class, id);
                if (task != null && !task.isRoutineInstance()) {
                    task.setScheduledDate(date);
                    moved++;
                }
            }
            return moved;
        });
    }

    /** "Omitir": deja constancia de que hoy no se hace (no se regenera). */
    public Task skip(long id) {
        return setStatus(id, TaskStatus.CANCELADA);
    }

    public void delete(long id) {
        sessionFactory.inTransaction(s -> {
            Task task = s.find(Task.class, id);
            if (task == null) {
                return;
            }
            if (task.isRoutineInstance()) {
                throw new ValidationException(ROUTINE_DELETE_ERROR);
            }
            s.remove(task);
        });
    }

    /**
     * Borra varias tareas en una sola transacción. Las generadas por una rutina no se borran
     * (se volverían a generar): se saltan y se cuentan aparte.
     */
    public DeleteResult deleteAll(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return new DeleteResult(0, 0);
        }
        return sessionFactory.fromTransaction(s -> {
            int deleted = 0;
            int skipped = 0;
            for (Long id : ids) {
                Task task = s.find(Task.class, id);
                if (task == null) {
                    continue;
                }
                if (task.isRoutineInstance()) {
                    skipped++;
                } else {
                    s.remove(task);
                    deleted++;
                }
            }
            return new DeleteResult(deleted, skipped);
        });
    }

    // ---------------------------------------------------------------- auxiliares

    private Task update(long id, java.util.function.Consumer<Task> change) {
        return sessionFactory.fromTransaction(s -> {
            Task task = taskDao.findByIdFetched(s, id)
                    .orElseThrow(() -> new ValidationException("La tarea ya no existe."));
            change.accept(task);
            return task;
        });
    }

    private void validate(Task task) {
        String title = task.getTitle() == null ? "" : task.getTitle().trim();
        if (title.isEmpty()) {
            throw new ValidationException("El título de la tarea es obligatorio.");
        }
        if (title.length() > 200) {
            throw new ValidationException("El título no puede superar 200 caracteres.");
        }
        task.setTitle(title);
        if (task.getDescription() != null && task.getDescription().isBlank()) {
            task.setDescription(null);
        }
        if (task.getArea() == null) {
            throw new ValidationException("Elige un área para la tarea.");
        }
        if (task.getStatus() == null) {
            task.setStatus(TaskStatus.PENDIENTE);
        }
        Integer minutes = task.getEstimatedMinutes();
        if (minutes != null && (minutes < 0 || minutes > 24 * 60)) {
            throw new ValidationException("La duración estimada debe estar entre 0 y 24 horas.");
        }
    }

    /** Sustituye la categoría desconectada por la gestionada y alinea el área con ella. */
    private static void attachCategory(Session s, Task task) {
        if (task.getCategory() == null) {
            return;
        }
        Category category = s.find(Category.class, task.getCategory().getId());
        task.setCategory(category);
        if (category != null) {
            task.setArea(category.getArea());
        }
    }

    private static Task initialized(Task task) {
        Hibernate.initialize(task.getCategory());
        Hibernate.initialize(task.getRoutine());
        return task;
    }
}
