package com.alejandro.synclife.dao;

import com.alejandro.synclife.model.entity.TimeEntry;
import com.alejandro.synclife.model.enums.Area;
import org.hibernate.Session;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Consultas del registro de tiempo. */
public class TimeEntryDao extends AbstractDao<TimeEntry> {

    public TimeEntryDao() {
        super(TimeEntry.class);
    }

    /** Registros de una tarea, del más reciente al más antiguo. */
    public List<TimeEntry> findByTask(Session session, long taskId) {
        return session.createSelectionQuery(
                        "from TimeEntry e where e.task.id = :taskId order by e.startedAt desc, e.id desc",
                        TimeEntry.class)
                .setParameter("taskId", taskId)
                .getResultList();
    }

    /** Minutos registrados por área con inicio en {@code [from, to)}. Las áreas sin registros valen 0. */
    public Map<Area, Long> minutesByArea(Session session, LocalDateTime from, LocalDateTime to) {
        List<Object[]> rows = session.createSelectionQuery(
                        "select e.task.area, sum(e.minutes) from TimeEntry e "
                                + "where e.startedAt >= :from and e.startedAt < :to group by e.task.area",
                        Object[].class)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
        Map<Area, Long> minutes = new EnumMap<>(Area.class);
        for (Area area : Area.values()) {
            minutes.put(area, 0L);
        }
        for (Object[] row : rows) {
            minutes.put((Area) row[0], ((Number) row[1]).longValue());
        }
        return minutes;
    }
}
