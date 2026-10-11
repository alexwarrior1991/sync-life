package com.alejandro.synclife.service;

import com.alejandro.synclife.dao.RoutineDao;
import com.alejandro.synclife.dao.TaskDao;
import com.alejandro.synclife.model.DaysOfWeek;
import com.alejandro.synclife.model.RoutineDay;
import com.alejandro.synclife.model.entity.Category;
import com.alejandro.synclife.model.entity.Routine;
import com.alejandro.synclife.model.entity.Task;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;

/**
 * Rutinas recurrentes y generación de sus tareas diarias.
 *
 * <p>Reglas:
 * <ul>
 *   <li>Se generan instancias desde hoy hasta el horizonte configurado; nunca para días pasados.</li>
 *   <li>La generación es idempotente (se comprueba lo existente y hay un índice único rutina+día).</li>
 *   <li>Al editar una rutina se regeneran solo las instancias PENDIENTE desde hoy; las completadas,
 *       en progreso u omitidas se conservan.</li>
 * </ul>
 */
public class RoutineService {

    private final SessionFactory sessionFactory;
    private final RoutineDao routineDao;
    private final TaskDao taskDao;
    private final Clock clock;
    private final IntSupplier horizonDays;

    public RoutineService(SessionFactory sessionFactory, RoutineDao routineDao, TaskDao taskDao,
                          Clock clock, IntSupplier horizonDays) {
        this.sessionFactory = sessionFactory;
        this.routineDao = routineDao;
        this.taskDao = taskDao;
        this.clock = clock;
        this.horizonDays = horizonDays;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public List<Routine> findAll() {
        return sessionFactory.fromTransaction(routineDao::findAllWithCategory);
    }

    public Routine save(Routine routine) {
        validate(routine);
        return sessionFactory.fromTransaction(s -> {
            if (routine.getCategory() != null) {
                Category category = s.find(Category.class, routine.getCategory().getId());
                routine.setCategory(category);
                if (category != null) {
                    routine.setArea(category.getArea());
                }
            }
            Routine saved = routineDao.save(s, routine);
            s.flush(); // necesitamos el id antes de generar instancias
            regenerate(s, saved);
            Hibernate.initialize(saved.getCategory());
            return saved;
        });
    }

    public Routine setActive(long id, boolean active) {
        return sessionFactory.fromTransaction(s -> {
            Routine routine = routineDao.findByIdWithCategory(s, id)
                    .orElseThrow(() -> new ValidationException("La rutina ya no existe."));
            routine.setActive(active);
            s.flush();
            regenerate(s, routine);
            return routine;
        });
    }

    /**
     * Borra la rutina. Sus instancias pendientes futuras desaparecen, las pasadas sin hacer se marcan
     * como omitidas y el resto (historial) se conserva sin vínculo a la rutina.
     */
    public void delete(long id) {
        sessionFactory.inTransaction(s -> {
            LocalDate today = today();
            taskDao.deletePendingInstancesFrom(s, id, today);
            taskDao.cancelOpenInstancesBefore(s, id, today);
            routineDao.delete(s, id);
        });
    }

    /** Genera las instancias que falten entre {@code from} y {@code to} (como pronto, hoy). */
    public int ensureInstances(LocalDate from, LocalDate to) {
        LocalDate today = today();
        LocalDate start = from.isBefore(today) ? today : from;
        if (start.isAfter(to)) {
            return 0;
        }
        return sessionFactory.fromTransaction(s -> {
            List<Routine> active = routineDao.findActive(s);
            if (active.isEmpty()) {
                return 0;
            }
            Set<RoutineDay> existing = taskDao.findRoutineDays(s, start, to);
            int created = 0;
            for (Routine routine : active) {
                created += createMissing(s, routine, start, to, existing);
            }
            return created;
        });
    }

    /** Genera las instancias desde hoy hasta el horizonte configurado en Ajustes. */
    public int ensureHorizon() {
        LocalDate today = today();
        return ensureInstances(today, today.plusDays(horizonDays.getAsInt() - 1L));
    }

    /** Vista previa de las próximas fechas, para el formulario (no toca la base de datos). */
    public List<LocalDate> preview(Routine routine, int count) {
        return RoutineScheduler.nextOccurrences(routine, today(), count);
    }

    private void regenerate(Session s, Routine routine) {
        LocalDate today = today();
        taskDao.deletePendingInstancesFrom(s, routine.getId(), today);
        if (!routine.isActive()) {
            return;
        }
        LocalDate to = today.plusDays(horizonDays.getAsInt() - 1L);
        Set<RoutineDay> existing = taskDao.findRoutineDays(s, today, to);
        createMissing(s, routine, today, to, existing);
    }

    private int createMissing(Session s, Routine routine, LocalDate from, LocalDate to,
                              Set<RoutineDay> existing) {
        int created = 0;
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        for (LocalDate day : RoutineScheduler.occurrences(routine, from, to)) {
            if (existing.add(new RoutineDay(routine.getId(), day))) {
                Task task = routine.newInstanceFor(day);
                if (task.getRemindAt() != null && task.getRemindAt().isBefore(now)) {
                    task.setRemindedAt(now); // la hora del aviso de hoy ya pasó: no se avisa tarde
                }
                s.persist(task);
                created++;
            }
        }
        return created;
    }

    private static void validate(Routine routine) {
        String name = routine.getName() == null ? "" : routine.getName().trim();
        if (name.isEmpty()) {
            throw new ValidationException("El nombre de la rutina es obligatorio.");
        }
        if (name.length() > 120) {
            throw new ValidationException("El nombre de la rutina no puede superar 120 caracteres.");
        }
        routine.setName(name);
        if (routine.getDescription() != null && routine.getDescription().isBlank()) {
            routine.setDescription(null);
        }
        if (routine.getArea() == null) {
            throw new ValidationException("Elige un área para la rutina.");
        }
        int mask = routine.getDaysMask();
        if (mask < 1 || mask > DaysOfWeek.ALL) {
            throw new ValidationException("Elige al menos un día de la semana.");
        }
        if (routine.getStartDate() == null) {
            throw new ValidationException("Indica desde qué fecha empieza la rutina.");
        }
        if (routine.getEndDate() != null && routine.getEndDate().isBefore(routine.getStartDate())) {
            throw new ValidationException("La fecha de fin no puede ser anterior a la de inicio.");
        }
        Integer minutes = routine.getDurationMinutes();
        if (minutes != null && (minutes <= 0 || minutes > 24 * 60)) {
            throw new ValidationException("La duración debe estar entre 1 minuto y 24 horas.");
        }
        Integer remind = routine.getRemindMinutesBefore();
        if (remind != null && (remind < 0 || remind > 24 * 60)) {
            throw new ValidationException("El aviso debe ser como mucho 24 horas antes.");
        }
    }
}
