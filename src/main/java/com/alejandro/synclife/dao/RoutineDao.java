package com.alejandro.synclife.dao;

import com.alejandro.synclife.model.entity.Routine;
import org.hibernate.Session;

import java.util.List;
import java.util.Optional;

public class RoutineDao extends AbstractDao<Routine> {

    private static final String BASE = "select r from Routine r left join fetch r.category ";

    public RoutineDao() {
        super(Routine.class);
    }

    public List<Routine> findAllWithCategory(Session session) {
        return session.createSelectionQuery(
                        BASE + "order by r.active desc, r.startTime nulls last, r.name", Routine.class)
                .getResultList();
    }

    public List<Routine> findActive(Session session) {
        return session.createSelectionQuery(BASE + "where r.active = true order by r.id", Routine.class)
                .getResultList();
    }

    public Optional<Routine> findByIdWithCategory(Session session, long id) {
        return session.createSelectionQuery(BASE + "where r.id = :id", Routine.class)
                .setParameter("id", id)
                .uniqueResultOptional();
    }
}
