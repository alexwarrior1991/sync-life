package com.alejandro.synclife.dao;

import com.alejandro.synclife.model.RoutineDay;
import com.alejandro.synclife.model.TaskFilter;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.TaskStatus;
import org.hibernate.Session;
import org.hibernate.query.SelectionQuery;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Consultas de tareas. Todas las que alimentan la UI traen la categoría y la rutina con
 * {@code join fetch}: la UI trabaja con entidades desconectadas y no puede cargar nada perezosamente.
 */
public class TaskDao extends AbstractDao<Task> {

    private static final String BASE =
            "select t from Task t left join fetch t.category left join fetch t.routine left join fetch t.goal ";
    private static final String DAY_ORDER = "t.startTime nulls last, t.priority desc, t.id";

    public TaskDao() {
        super(Task.class);
    }

    public Optional<Task> findByIdFetched(Session session, long id) {
        return session.createSelectionQuery(BASE + "where t.id = :id", Task.class)
                .setParameter("id", id)
                .uniqueResultOptional();
    }

    public List<Task> findForDay(Session session, LocalDate day) {
        return session.createSelectionQuery(BASE + "where t.scheduledDate = :day order by " + DAY_ORDER, Task.class)
                .setParameter("day", day)
                .getResultList();
    }

    public List<Task> findBetween(Session session, LocalDate from, LocalDate to) {
        return session.createSelectionQuery(
                        BASE + "where t.scheduledDate between :from and :to order by t.scheduledDate, " + DAY_ORDER,
                        Task.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    /** Tareas puntuales abiertas que se quedaron en días anteriores (las rutinas no se arrastran). */
    public List<Task> findCarryOver(Session session, LocalDate today) {
        return session.createSelectionQuery(
                        BASE + "where t.routine is null and t.status in :open and t.scheduledDate < :today "
                                + "order by t.scheduledDate, t.priority desc, t.id", Task.class)
                .setParameter("open", TaskStatus.OPEN)
                .setParameter("today", today)
                .getResultList();
    }

    /** Tareas abiertas con fecha límite hasta {@code until} (incluye las ya vencidas). */
    public List<Task> findDueUntil(Session session, LocalDate until) {
        return session.createSelectionQuery(
                        BASE + "where t.status in :open and t.dueDate <= :until "
                                + "order by t.dueDate, t.priority desc, t.id", Task.class)
                .setParameter("open", TaskStatus.OPEN)
                .setParameter("until", until)
                .getResultList();
    }

    public List<Task> findUnscheduled(Session session) {
        return session.createSelectionQuery(
                        BASE + "where t.status in :open and t.scheduledDate is null "
                                + "order by t.priority desc, t.dueDate nulls last, t.id", Task.class)
                .setParameter("open", TaskStatus.OPEN)
                .getResultList();
    }

    /** Tareas puntuales abiertas para la matriz de Eisenhower ({@code area == null} = todas). */
    public List<Task> findOpenForMatrix(Session session, Area area) {
        SelectionQuery<Task> query = session.createSelectionQuery(
                BASE + "where t.status in :open and t.routine is null"
                        + (area == null ? "" : " and t.area = :area")
                        + " order by t.dueDate nulls last, t.priority desc, t.scheduledDate nulls last, t.id",
                Task.class);
        query.setParameter("open", TaskStatus.OPEN);
        if (area != null) {
            query.setParameter("area", area);
        }
        return query.getResultList();
    }

    /** Tareas completadas con {@code completedAt} en {@code [from, to)}, sea cual sea su día programado. */
    public List<Task> findCompletedBetween(Session session, LocalDateTime from, LocalDateTime to) {
        return session.createSelectionQuery(
                        BASE + "where t.status = :done and t.completedAt >= :from and t.completedAt < :to "
                                + "order by t.completedAt, t.id", Task.class)
                .setParameter("done", TaskStatus.COMPLETADA)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    /** Tareas vinculadas a un objetivo: primero las abiertas (por fecha), luego el resto. */
    public List<Task> findByGoal(Session session, long goalId) {
        return session.createSelectionQuery(
                        BASE + "where t.goal.id = :goalId order by "
                                + "case when t.status in :open then 0 else 1 end, "
                                + "t.scheduledDate nulls last, t.priority desc, t.id", Task.class)
                .setParameter("goalId", goalId)
                .setParameter("open", TaskStatus.OPEN)
                .getResultList();
    }

    /** Tareas que se pueden vincular a un objetivo: puntuales, abiertas y sin objetivo. */
    public List<Task> findLinkable(Session session) {
        return session.createSelectionQuery(
                        BASE + "where t.goal is null and t.routine is null and t.status in :open "
                                + "order by t.area, t.scheduledDate nulls last, t.priority desc, t.id", Task.class)
                .setParameter("open", TaskStatus.OPEN)
                .getResultList();
    }

    /** Número de tareas [total, completadas] por objetivo, sin contar las canceladas. */
    public Map<Long, long[]> countByGoal(Session session) {
        List<Object[]> rows = session.createSelectionQuery(
                        "select t.goal.id, count(t), sum(case when t.status = :done then 1 else 0 end) from Task t "
                                + "where t.goal is not null and t.status <> :cancelled group by t.goal.id",
                        Object[].class)
                .setParameter("done", TaskStatus.COMPLETADA)
                .setParameter("cancelled", TaskStatus.CANCELADA)
                .getResultList();
        Map<Long, long[]> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((Long) row[0], new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        return counts;
    }

    public List<Task> search(Session session, TaskFilter filter) {
        StringBuilder hql = new StringBuilder(BASE).append("where 1 = 1");
        Map<String, Object> params = new LinkedHashMap<>();

        if (filter.getArea() != null) {
            hql.append(" and t.area = :area");
            params.put("area", filter.getArea());
        }
        if (filter.getCategoryId() != null) {
            hql.append(" and t.category.id = :categoryId");
            params.put("categoryId", filter.getCategoryId());
        }
        if (!filter.getStatuses().containsAll(EnumSet.allOf(TaskStatus.class))) {
            hql.append(" and t.status in :statuses");
            params.put("statuses", filter.getStatuses());
        }
        if (filter.getPriority() != null) {
            hql.append(" and t.priority = :priority");
            params.put("priority", filter.getPriority());
        }
        if (filter.isUnscheduledOnly()) {
            hql.append(" and t.scheduledDate is null");
        } else {
            if (filter.getScheduledFrom() != null) {
                hql.append(" and t.scheduledDate >= :from");
                params.put("from", filter.getScheduledFrom());
            }
            if (filter.getScheduledTo() != null) {
                hql.append(" and t.scheduledDate <= :to");
                params.put("to", filter.getScheduledTo());
            }
        }
        if (filter.getDueBefore() != null) {
            hql.append(" and t.dueDate < :dueBefore");
            params.put("dueBefore", filter.getDueBefore());
        }
        if (!filter.isIncludeRoutineInstances()) {
            hql.append(" and t.routine is null");
        }
        hql.append(" order by t.scheduledDate nulls last, ").append(DAY_ORDER);

        SelectionQuery<Task> query = session.createSelectionQuery(hql.toString(), Task.class);
        params.forEach(query::setParameter);
        return query.getResultList();
    }

    /** Qué (rutina, día) ya tienen instancia en el rango, para no duplicarlas. */
    public Set<RoutineDay> findRoutineDays(Session session, LocalDate from, LocalDate to) {
        List<Object[]> rows = session.createSelectionQuery(
                        "select t.routine.id, t.scheduledDate from Task t "
                                + "where t.routine is not null and t.scheduledDate between :from and :to",
                        Object[].class)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
        Set<RoutineDay> days = new HashSet<>();
        for (Object[] row : rows) {
            days.add(new RoutineDay((Long) row[0], (LocalDate) row[1]));
        }
        return days;
    }

    /**
     * Borra las instancias PENDIENTE de una rutina desde {@code from} (para regenerarlas tras editarla).
     * Las que ya tienen tiempo registrado se conservan: borrarlas se llevaría también ese tiempo.
     */
    public int deletePendingInstancesFrom(Session session, long routineId, LocalDate from) {
        return session.createMutationQuery(
                        "delete from Task t where t.routine.id = :routineId "
                                + "and t.status = :pending and t.scheduledDate >= :from "
                                + "and not exists (select 1 from TimeEntry e where e.task = t)")
                .setParameter("routineId", routineId)
                .setParameter("pending", TaskStatus.PENDIENTE)
                .setParameter("from", from)
                .executeUpdate();
    }

    /** Marca como CANCELADA las instancias abiertas de una rutina anteriores a {@code before}. */
    public int cancelOpenInstancesBefore(Session session, long routineId, LocalDate before) {
        return session.createMutationQuery(
                        "update Task t set t.status = :cancelled, t.completedAt = null "
                                + "where t.routine.id = :routineId and t.status in :open and t.scheduledDate < :before")
                .setParameter("cancelled", TaskStatus.CANCELADA)
                .setParameter("routineId", routineId)
                .setParameter("open", TaskStatus.OPEN)
                .setParameter("before", before)
                .executeUpdate();
    }

    /** Número de tareas puntuales abiertas por id de categoría (sin contar las instancias de rutinas). */
    public Map<Long, Long> countOpenByCategory(Session session) {
        List<Object[]> rows = session.createSelectionQuery(
                        "select t.category.id, count(t) from Task t "
                                + "where t.category is not null and t.routine is null and t.status in :open "
                                + "group by t.category.id",
                        Object[].class)
                .setParameter("open", TaskStatus.OPEN)
                .getResultList();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    public long countByCategory(Session session, long categoryId) {
        return session.createSelectionQuery(
                        "select count(t) from Task t where t.category.id = :id", Long.class)
                .setParameter("id", categoryId)
                .getSingleResult();
    }
}
