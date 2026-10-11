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

    /** Margen dentro del cual una estimación se considera acertada (±20 %). */
    public static final double ACCURACY_TOLERANCE = 0.20;
    /** Cuántas desviaciones se muestran como mucho en la tabla "Estimado frente a real". */
    public static final int TOP_DEVIATIONS = 10;

    public record AreaStats(Area area, long completed, long total, long minutesCompleted, long minutesTracked) {
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

    /** Una tarea completada con estimación y tiempo registrado. */
    public record Deviation(String title, Area area, int estimatedMinutes, int trackedMinutes) {
        /** Real / estimado: 1 = clavado, 1,5 = un 50 % más de lo previsto. */
        public double ratio() {
            return (double) trackedMinutes / estimatedMinutes;
        }

        public boolean onTarget() {
            return Math.abs(ratio() - 1) <= ACCURACY_TOLERANCE;
        }
    }

    /**
     * Precisión de las estimaciones de las tareas completadas en el periodo que tienen estimación y
     * tiempo registrado. {@code largest} son las mayores desviaciones, de mayor a menor.
     */
    public record EstimateAccuracy(int tasks, int onTarget, int over, int under,
                                   long estimatedMinutes, long trackedMinutes, List<Deviation> largest) {
        public double onTargetRate() {
            return tasks == 0 ? 0 : (double) onTarget / tasks;
        }

        /** Real / estimado sumando todas las tareas (0 si no hay ninguna). */
        public double overallRatio() {
            return estimatedMinutes == 0 ? 0 : (double) trackedMinutes / estimatedMinutes;
        }
    }

    public record Snapshot(List<AreaStats> byArea, List<DayStats> byDay, List<RoutineAdherence> routines,
                           EstimateAccuracy accuracy) {
        public long completed() {
            return byArea.stream().mapToLong(AreaStats::completed).sum();
        }

        public long total() {
            return byArea.stream().mapToLong(AreaStats::total).sum();
        }

        public long minutesCompleted() {
            return byArea.stream().mapToLong(AreaStats::minutesCompleted).sum();
        }

        public long minutesTracked() {
            return byArea.stream().mapToLong(AreaStats::minutesTracked).sum();
        }
    }

    private final TaskService taskService;
    private final TimeTrackingService timeTracking;
    private final Clock clock;

    public StatsService(TaskService taskService, TimeTrackingService timeTracking, Clock clock) {
        this.taskService = taskService;
        this.timeTracking = timeTracking;
        this.clock = clock;
    }

    public Snapshot compute(LocalDate from, LocalDate to) {
        List<Task> tasks = taskService.tasksBetween(from, to);
        Map<Area, Long> tracked = timeTracking.minutesByArea(from, to);
        LocalDate today = LocalDate.now(clock);

        Map<Area, long[]> areas = new EnumMap<>(Area.class); // [completadas, total, minutos estimados]
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
        areas.forEach((area, v) -> byArea.add(new AreaStats(area, v[0], v[1], v[2], tracked.getOrDefault(area, 0L))));
        List<DayStats> byDay = new ArrayList<>();
        days.forEach((day, v) -> byDay.add(new DayStats(day, v[0], v[1])));
        List<RoutineAdherence> adherence = new ArrayList<>();
        routines.forEach((name, v) -> adherence.add(new RoutineAdherence(name, v[0], v[1])));
        adherence.sort(Comparator.comparingDouble(RoutineAdherence::rate).reversed());
        return new Snapshot(byArea, byDay, adherence, accuracy(taskService.completedBetween(from, to)));
    }

    /** Compara lo estimado con lo registrado en las tareas completadas que tienen ambas cosas. */
    static EstimateAccuracy accuracy(List<Task> completed) {
        List<Deviation> deviations = completed.stream()
                .filter(t -> t.getEstimatedMinutes() != null && t.getEstimatedMinutes() > 0 && t.getTrackedMinutes() > 0)
                .map(t -> new Deviation(t.getTitle(), t.getArea(), t.getEstimatedMinutes(), t.getTrackedMinutes()))
                .toList();
        int onTarget = 0;
        int over = 0;
        int under = 0;
        long estimated = 0;
        long trackedTotal = 0;
        for (Deviation deviation : deviations) {
            if (deviation.onTarget()) {
                onTarget++;
            } else if (deviation.ratio() > 1) {
                over++;
            } else {
                under++;
            }
            estimated += deviation.estimatedMinutes();
            trackedTotal += deviation.trackedMinutes();
        }
        List<Deviation> largest = deviations.stream()
                .sorted(Comparator.comparingDouble((Deviation d) -> Math.abs(Math.log(d.ratio()))).reversed())
                .limit(TOP_DEVIATIONS)
                .toList();
        return new EstimateAccuracy(deviations.size(), onTarget, over, under, estimated, trackedTotal, largest);
    }
}
