package com.alejandro.synclife.service;

import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.TaskStatus;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Estadísticas sencillas calculadas en memoria sobre las tareas de un periodo. */
public class StatsService {

    public record AreaStats(Area area, long completed, long total, long minutesCompleted) {
        public double rate() {
            return total == 0 ? 0 : (double) completed / total;
        }
    }

    public record DayStats(LocalDate day, long completed, long open) {
    }

    public record RoutineAdherence(String routine, long completed, long total) {
        public double rate() {
            return total == 0 ? 0 : (double) completed / total;
        }
    }

    public record Snapshot(List<AreaStats> byArea, List<DayStats> byDay, List<RoutineAdherence> routines) {
        public long completed() {
            return byArea.stream().mapToLong(AreaStats::completed).sum();
        }

        public long total() {
            return byArea.stream().mapToLong(AreaStats::total).sum();
        }

        public long minutesCompleted() {
            return byArea.stream().mapToLong(AreaStats::minutesCompleted).sum();
        }
    }

    private final TaskService taskService;
    private final Clock clock;

    public StatsService(TaskService taskService, Clock clock) {
        this.taskService = taskService;
        this.clock = clock;
    }

    public Snapshot compute(LocalDate from, LocalDate to) {
        List<Task> tasks = taskService.tasksBetween(from, to);
        LocalDate today = LocalDate.now(clock);

        Map<Area, long[]> areas = new EnumMap<>(Area.class); // [completadas, total, minutos]
        for (Area area : Area.values()) {
            areas.put(area, new long[3]);
        }
        Map<LocalDate, long[]> days = new TreeMap<>(); // [completadas, abiertas]
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            days.put(d, new long[2]);
        }
        Map<String, long[]> routines = new LinkedHashMap<>(); // [completadas, total]

        for (Task task : tasks) {
            boolean done = task.getStatus() == TaskStatus.COMPLETADA;
            if (task.getStatus() != TaskStatus.CANCELADA) {
                long[] a = areas.get(task.getArea());
                a[1]++;
                if (done) {
                    a[0]++;
                    a[2] += task.getEstimatedMinutes() == null ? 0 : task.getEstimatedMinutes();
                }
                long[] d = days.get(task.getScheduledDate());
                if (d != null) {
                    d[done ? 0 : 1]++;
                }
            }
            // Cumplimiento de rutinas: solo días ya vividos; omitir cuenta como no cumplido
            if (task.isRoutineInstance() && !task.getScheduledDate().isAfter(today)) {
                long[] r = routines.computeIfAbsent(task.getRoutine().getName(), k -> new long[2]);
                r[1]++;
                if (done) {
                    r[0]++;
                }
            }
        }

        List<AreaStats> byArea = new ArrayList<>();
        areas.forEach((area, v) -> byArea.add(new AreaStats(area, v[0], v[1], v[2])));
        List<DayStats> byDay = new ArrayList<>();
        days.forEach((day, v) -> byDay.add(new DayStats(day, v[0], v[1])));
        List<RoutineAdherence> adherence = new ArrayList<>();
        routines.forEach((name, v) -> adherence.add(new RoutineAdherence(name, v[0], v[1])));
        adherence.sort(Comparator.comparingDouble(RoutineAdherence::rate).reversed());
        return new Snapshot(byArea, byDay, adherence);
    }
}
