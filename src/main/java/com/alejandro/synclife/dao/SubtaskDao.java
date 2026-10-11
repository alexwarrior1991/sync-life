package com.alejandro.synclife.dao;

import com.alejandro.synclife.model.entity.Subtask;
import org.hibernate.Session;

import java.util.Collection;
import java.util.List;

/** Consultas de la lista de comprobación de una tarea. */
public class SubtaskDao extends AbstractDao<Subtask> {

    public SubtaskDao() {
        super(Subtask.class);
    }

    /** Las listas de comprobación de varias tareas de una vez (para exportar). */
    public List<Subtask> findByTasks(Session session, Collection<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return List.of();
        }
        return session.createSelectionQuery(
                        "from Subtask s where s.task.id in :ids order by s.task.id, s.sortOrder, s.id", Subtask.class)
                .setParameter("ids", taskIds)
                .getResultList();
    }

    public List<Subtask> findByTask(Session session, long taskId) {
        return session.createSelectionQuery(
                        "from Subtask s where s.task.id = :taskId order by s.sortOrder, s.id", Subtask.class)
                .setParameter("taskId", taskId)
                .getResultList();
    }
}
