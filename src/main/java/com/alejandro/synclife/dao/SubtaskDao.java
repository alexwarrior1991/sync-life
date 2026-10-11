package com.alejandro.synclife.dao;

import com.alejandro.synclife.model.entity.Subtask;
import org.hibernate.Session;

import java.util.List;

/** Consultas de la lista de comprobación de una tarea. */
public class SubtaskDao extends AbstractDao<Subtask> {

    public SubtaskDao() {
        super(Subtask.class);
    }

    public List<Subtask> findByTask(Session session, long taskId) {
        return session.createSelectionQuery(
                        "from Subtask s where s.task.id = :taskId order by s.sortOrder, s.id", Subtask.class)
                .setParameter("taskId", taskId)
                .getResultList();
    }
}
