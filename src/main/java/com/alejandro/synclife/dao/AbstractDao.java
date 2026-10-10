package com.alejandro.synclife.dao;

import com.alejandro.synclife.model.entity.BaseEntity;
import org.hibernate.Session;

import java.util.List;
import java.util.Optional;

/**
 * Operaciones CRUD comunes. Los DAO no abren transacciones ni guardan estado:
 * reciben la {@link Session} del servicio, que decide los límites de la transacción.
 */
public abstract class AbstractDao<T extends BaseEntity> {

    private final Class<T> type;

    protected AbstractDao(Class<T> type) {
        this.type = type;
    }

    public Optional<T> findById(Session session, long id) {
        return Optional.ofNullable(session.find(type, id));
    }

    public List<T> findAll(Session session) {
        return session.createSelectionQuery("from " + type.getSimpleName(), type).getResultList();
    }

    /** Inserta si es nueva; si no, fusiona el estado de la entidad desconectada. */
    public T save(Session session, T entity) {
        if (entity.isNew()) {
            session.persist(entity);
            return entity;
        }
        return session.merge(entity);
    }

    public void delete(Session session, long id) {
        T entity = session.find(type, id);
        if (entity != null) {
            session.remove(entity);
        }
    }
}
