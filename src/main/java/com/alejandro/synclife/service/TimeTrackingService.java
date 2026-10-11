package com.alejandro.synclife.service;

import com.alejandro.synclife.dao.TimeEntryDao;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.entity.TimeEntry;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.TimeSource;
import org.hibernate.SessionFactory;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/** Registro del tiempo real dedicado a las tareas (Pomodoro o apuntes manuales). */
public class TimeTrackingService {

    public static final int MAX_MINUTES = 24 * 60;

    private final SessionFactory sessionFactory;
    private final TimeEntryDao timeEntryDao;
    private final Clock clock;

    public TimeTrackingService(SessionFactory sessionFactory, TimeEntryDao timeEntryDao, Clock clock) {
        this.sessionFactory = sessionFactory;
        this.timeEntryDao = timeEntryDao;
        this.clock = clock;
    }

    /**
     * Apunta {@code minutes} minutos de trabajo efectivo hechos entre {@code start} y {@code end}.
     * Los minutos pueden ser menos que el intervalo (pausas), nunca más.
     */
    public TimeEntry record(long taskId, LocalDateTime start, LocalDateTime end, int minutes, TimeSource source) {
        LocalDateTime from = start.truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime to = end.truncatedTo(ChronoUnit.SECONDS);
        if (to.isBefore(from)) {
            throw new ValidationException("El final del registro no puede ser anterior a su inicio.");
        }
        validateMinutes(minutes);
        return sessionFactory.fromTransaction(s -> {
            Task task = s.find(Task.class, taskId);
            if (task == null) {
                throw new ValidationException("La tarea ya no existe.");
            }
            TimeEntry entry = new TimeEntry(task, from, to, minutes, source);
            s.persist(entry);
            return entry;
        });
    }

    /** Apunte manual de {@code minutes} minutos que terminan ahora. */
    public TimeEntry addManual(long taskId, int minutes) {
        validateMinutes(minutes);
        LocalDateTime now = LocalDateTime.now(clock);
        return record(taskId, now.minusMinutes(minutes), now, minutes, TimeSource.MANUAL);
    }

    /** Registros de una tarea, del más reciente al más antiguo. */
    public List<TimeEntry> entries(long taskId) {
        return sessionFactory.fromTransaction(s -> timeEntryDao.findByTask(s, taskId));
    }

    public void deleteEntry(long entryId) {
        sessionFactory.inTransaction(s -> timeEntryDao.delete(s, entryId));
    }

    /** Minutos registrados por área en los días {@code [from, to]}, según cuándo empezó cada sesión. */
    public Map<Area, Long> minutesByArea(LocalDate from, LocalDate to) {
        return sessionFactory.fromTransaction(s ->
                timeEntryDao.minutesByArea(s, from.atStartOfDay(), to.plusDays(1).atStartOfDay()));
    }

    private static void validateMinutes(int minutes) {
        if (minutes < 1 || minutes > MAX_MINUTES) {
            throw new ValidationException("El tiempo registrado debe estar entre 1 minuto y 24 horas.");
        }
    }
}
