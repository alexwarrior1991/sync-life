package com.alejandro.synclife.dao;

import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.entity.WeeklyReview;
import org.hibernate.Session;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Consultas de las revisiones semanales y sus prioridades. */
public class WeeklyReviewDao extends AbstractDao<WeeklyReview> {

    public WeeklyReviewDao() {
        super(WeeklyReview.class);
    }

    public Optional<WeeklyReview> findByWeek(Session session, LocalDate weekStart) {
        return session.createSelectionQuery("from WeeklyReview r where r.weekStart = :week", WeeklyReview.class)
                .setParameter("week", weekStart)
                .uniqueResultOptional();
    }

    /** Las revisiones más recientes primero. */
    public List<WeeklyReview> findRecent(Session session, int limit) {
        return session.createSelectionQuery("from WeeklyReview r order by r.weekStart desc", WeeklyReview.class)
                .setMaxResults(limit)
                .getResultList();
    }

    /** Tareas prioritarias de la semana que empieza en {@code weekStart}, en orden (con sus datos cargados). */
    public List<Task> findPriorityTasks(Session session, LocalDate weekStart) {
        return session.createSelectionQuery(
                        "select t from WeeklyPriority p join p.task t "
                                + "left join fetch t.category left join fetch t.routine left join fetch t.goal "
                                + "where p.review.weekStart = :week order by p.sortOrder", Task.class)
                .setParameter("week", weekStart)
                .getResultList();
    }

    /**
     * Borra las prioridades de una revisión con una sola sentencia. Así se pueden volver a insertar
     * en los mismos huecos en la misma transacción (Hibernate ejecuta los INSERT antes que los DELETE
     * de entidades, y chocarían con la restricción de unicidad).
     */
    public int deletePriorities(Session session, long reviewId) {
        return session.createMutationQuery("delete from WeeklyPriority p where p.review.id = :id")
                .setParameter("id", reviewId)
                .executeUpdate();
    }
}
