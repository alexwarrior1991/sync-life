package com.alejandro.synclife.service;

import com.alejandro.synclife.dao.TaskDao;
import com.alejandro.synclife.model.entity.Task;
import org.hibernate.SessionFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Recordatorios de tareas. La interfaz pregunta cada poco por los avisos vencidos con {@link #claimDue()}:
 * cada aviso se entrega una sola vez (queda marcado en la misma transacción en que se lee).
 */
public class ReminderService {

    /** Un aviso que venció hace más de esto (p. ej. con la aplicación cerrada) se da por perdido. */
    public static final Duration MISSED_AFTER = Duration.ofHours(2);

    /**
     * Avisos para mostrar ahora: los recientes, uno a uno; los perdidos (vencidos hace más de
     * {@link #MISSED_AFTER}), juntos en un resumen.
     */
    public record Due(List<Task> fresh, List<Task> missed) {
        public boolean isEmpty() {
            return fresh.isEmpty() && missed.isEmpty();
        }
    }

    private final SessionFactory sessionFactory;
    private final TaskDao taskDao;
    private final Clock clock;

    public ReminderService(SessionFactory sessionFactory, TaskDao taskDao, Clock clock) {
        this.sessionFactory = sessionFactory;
        this.taskDao = taskDao;
        this.clock = clock;
    }

    /** Devuelve los avisos vencidos aún no mostrados y los marca como mostrados. */
    public Due claimDue() {
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        return sessionFactory.fromTransaction(s -> {
            List<Task> fresh = new ArrayList<>();
            List<Task> missed = new ArrayList<>();
            for (Task task : taskDao.findDueReminders(s, now)) {
                task.setRemindedAt(now);
                (task.getRemindAt().isBefore(now.minus(MISSED_AFTER)) ? missed : fresh).add(task);
            }
            return new Due(fresh, missed);
        });
    }

    /** "Posponer": vuelve a avisar dentro de {@code delay}. */
    public Task snooze(long taskId, Duration delay) {
        if (delay.isNegative() || delay.isZero()) {
            throw new ValidationException("Indica cuánto tiempo posponer el aviso.");
        }
        LocalDateTime when = LocalDateTime.now(clock).withNano(0).plus(delay);
        return sessionFactory.fromTransaction(s -> {
            Task task = taskDao.findByIdFetched(s, taskId)
                    .orElseThrow(() -> new ValidationException("La tarea ya no existe."));
            task.setRemindAt(when);
            task.setRemindedAt(null);
            return task;
        });
    }
}
