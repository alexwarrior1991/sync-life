package com.alejandro.synclife.service;

import com.alejandro.synclife.dao.SubtaskDao;
import com.alejandro.synclife.dao.TaskDao;
import com.alejandro.synclife.model.ChecklistItem;
import com.alejandro.synclife.model.TaskFilter;
import com.alejandro.synclife.model.entity.Category;
import com.alejandro.synclife.model.entity.Goal;
import com.alejandro.synclife.model.entity.Subtask;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.Quadrant;
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
import java.util.EnumMap;
import java.util.HashMap;
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
    static final String ROUTINE_CHECKLIST_ERROR =
            "Las tareas de una rutina no tienen lista de comprobación: se regeneran al editar la rutina.";
    static final String ROUTINE_MATRIX_ERROR =
            "Las tareas de una rutina no se clasifican en la matriz de Eisenhower.";
    static final String ROUTINE_GOAL_ERROR =
            "Las tareas de una rutina no se vinculan a objetivos: se regeneran al editar la rutina.";
    public static final int MAX_CHECKLIST_ITEMS = 50;

    /** Contenido de la matriz de Eisenhower: tareas abiertas por cuadrante y las que aún no tienen. */
    public record MatrixData(Map<Quadrant, List<Task>> quadrants, List<Task> unclassified) {
        public List<Task> of(Quadrant quadrant) {
            return quadrants.getOrDefault(quadrant, List.of());
        }
    }

    private final SessionFactory sessionFactory;
    private final TaskDao taskDao;
    private final SubtaskDao subtaskDao;
    private final Clock clock;

    public TaskService(SessionFactory sessionFactory, TaskDao taskDao, SubtaskDao subtaskDao, Clock clock) {
        this.sessionFactory = sessionFactory;
        this.taskDao = taskDao;
        this.subtaskDao = subtaskDao;
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

    /** Lista de comprobación de la tarea, en orden. */
    public List<ChecklistItem> checklist(long taskId) {
        return sessionFactory.fromTransaction(s -> subtaskDao.findByTask(s, taskId).stream()
                .map(sub -> new ChecklistItem(sub.getId(), sub.getTitle(), sub.isDone()))
                .toList());
    }

    /** Tareas puntuales abiertas repartidas por cuadrante ({@code area == null} = todas las áreas). */
    public MatrixData matrix(Area area) {
        List<Task> open = sessionFactory.fromTransaction(s -> taskDao.findOpenForMatrix(s, area));
        Map<Quadrant, List<Task>> quadrants = new EnumMap<>(Quadrant.class);
        for (Quadrant quadrant : Quadrant.values()) {
            quadrants.put(quadrant, new ArrayList<>());
        }
        List<Task> unclassified = new ArrayList<>();
        for (Task task : open) {
            Quadrant quadrant = task.getQuadrant();
            (quadrant == null ? unclassified : quadrants.get(quadrant)).add(task);
        }
        return new MatrixData(quadrants, unclassified);
    }

    /** Tareas completadas entre dos días (ambos incluidos) según su fecha de completado. */
    public List<Task> completedBetween(LocalDate from, LocalDate to) {
        return sessionFactory.fromTransaction(s ->
                taskDao.findCompletedBetween(s, from.atStartOfDay(), to.plusDays(1).atStartOfDay()));
    }

    // ---------------------------------------------------------------- cambios

    public Task save(Task task) {
        return save(task, null);
    }

    /**
     * Guarda la tarea y, si {@code checklist} no es null, deja su lista de comprobación exactamente así
     * (en ese orden): actualiza los elementos con id, crea los nuevos y borra los que ya no están.
     * Todo en una transacción.
     */
    public Task save(Task task, List<ChecklistItem> checklist) {
        validate(task);
        List<ChecklistItem> items = checklist == null ? null : validChecklist(checklist);
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
            if (task.isRoutineInstance()) {
                if (items != null && !items.isEmpty()) {
                    throw new ValidationException(ROUTINE_CHECKLIST_ERROR);
                }
                if (task.getUrgent() != null || task.getImportant() != null) {
                    throw new ValidationException(ROUTINE_MATRIX_ERROR);
                }
                if (task.getGoal() != null) {
                    throw new ValidationException(ROUTINE_GOAL_ERROR);
                }
            }
            attachCategory(s, task);
            attachGoal(s, task);
            if (task.getStatus() == TaskStatus.COMPLETADA) {
                if (task.getCompletedAt() == null) {
                    task.setCompletedAt(now().withNano(0));
                }
            } else {
                task.setCompletedAt(null);
            }
            Task saved = taskDao.save(s, task);
            if (items != null) {
                syncChecklist(s, saved, items);
            }
            s.flush();
            s.refresh(saved); // recalcula subtareas y minutos registrados (@Formula)
            return initialized(saved);
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

    /** Clasifica la tarea en un cuadrante de Eisenhower ({@code null} = sin clasificar). */
    public Task setQuadrant(long id, Quadrant quadrant) {
        return update(id, task -> {
            if (task.isRoutineInstance()) {
                throw new ValidationException(ROUTINE_MATRIX_ERROR);
            }
            task.setQuadrant(quadrant);
        });
    }

    /**
     * Se va a trabajar en la tarea (p. ej. empieza un Pomodoro): una pendiente pasa a "En progreso".
     * Así, si es de una rutina, editar la rutina ya no la regenera.
     */
    public Task startWorking(long id) {
        return update(id, task -> {
            if (!task.isOpen()) {
                throw new ValidationException("La tarea ya está " + task.getStatus().getDisplayName().toLowerCase()
                        + ": reábrela para seguir trabajando en ella.");
            }
            task.setStatus(TaskStatus.EN_PROGRESO);
        });
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

    /** Recorta los títulos, descarta los vacíos y comprueba los límites. */
    private static List<ChecklistItem> validChecklist(List<ChecklistItem> checklist) {
        List<ChecklistItem> items = new ArrayList<>();
        for (ChecklistItem item : checklist) {
            String title = item.title() == null ? "" : item.title().trim();
            if (title.isEmpty()) {
                continue;
            }
            if (title.length() > 200) {
                throw new ValidationException("Cada paso de la lista de comprobación admite como mucho 200 caracteres.");
            }
            items.add(item.withTitle(title));
        }
        if (items.size() > MAX_CHECKLIST_ITEMS) {
            throw new ValidationException("La lista de comprobación admite como mucho "
                    + MAX_CHECKLIST_ITEMS + " pasos.");
        }
        return items;
    }

    private void syncChecklist(Session s, Task task, List<ChecklistItem> items) {
        Map<Long, Subtask> existing = new HashMap<>();
        for (Subtask subtask : subtaskDao.findByTask(s, task.getId())) {
            existing.put(subtask.getId(), subtask);
        }
        int order = 0;
        for (ChecklistItem item : items) {
            // Un id que no es de esta tarea (p. ej. de una copia) se trata como un paso nuevo
            Subtask subtask = item.id() == null ? null : existing.remove(item.id());
            if (subtask == null) {
                subtask = new Subtask(task, item.title());
                subtask.setDone(item.done());
                subtask.setSortOrder(order++);
                s.persist(subtask);
            } else {
                subtask.setTitle(item.title());
                subtask.setDone(item.done());
                subtask.setSortOrder(order++);
            }
        }
        existing.values().forEach(s::remove);
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

    /** Sustituye el objetivo desconectado por el gestionado (null si ya no existe). */
    private static void attachGoal(Session s, Task task) {
        if (task.getGoal() != null) {
            task.setGoal(s.find(Goal.class, task.getGoal().getId()));
        }
    }

    private static Task initialized(Task task) {
        Hibernate.initialize(task.getCategory());
        Hibernate.initialize(task.getRoutine());
        Hibernate.initialize(task.getGoal());
        return task;
    }
}
