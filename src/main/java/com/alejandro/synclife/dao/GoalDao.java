package com.alejandro.synclife.dao;

import com.alejandro.synclife.model.entity.Goal;
import com.alejandro.synclife.model.enums.GoalStatus;
import org.hibernate.Session;

import java.util.List;

/** Consultas de objetivos. */
public class GoalDao extends AbstractDao<Goal> {

    public GoalDao() {
        super(Goal.class);
    }

    /** En curso primero (por fecha objetivo), después los cerrados más recientes. */
    public List<Goal> findAll(Session session, boolean includeClosed) {
        return session.createSelectionQuery(
                        "from Goal g " + (includeClosed ? "" : "where g.status = :open ")
                                + "order by case when g.status = :open then 0 else 1 end, "
                                + "g.targetDate nulls last, g.completedAt desc, g.title", Goal.class)
                .setParameter("open", GoalStatus.EN_CURSO)
                .getResultList();
    }
}
