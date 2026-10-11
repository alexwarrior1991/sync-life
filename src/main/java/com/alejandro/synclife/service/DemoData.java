package com.alejandro.synclife.service;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.model.ChecklistItem;
import com.alejandro.synclife.model.DaysOfWeek;
import com.alejandro.synclife.model.entity.Category;
import com.alejandro.synclife.model.entity.Routine;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.Priority;
import com.alejandro.synclife.model.enums.Quadrant;
import com.alejandro.synclife.model.enums.TimeSource;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Datos de ejemplo para probar la aplicación (Ajustes → "Cargar datos de ejemplo"). */
public final class DemoData {

    private DemoData() {
    }

    public static void seed(AppContext context) {
        TaskService tasks = context.tasks();
        RoutineService routines = context.routines();
        TimeTrackingService time = context.timeTracking();
        LocalDate today = tasks.today();
        Map<String, Category> byName = context.categories().findAll(true).stream()
                .collect(Collectors.toMap(Category::getName, Function.identity(), (a, b) -> a));

        routine(routines, byName, "Revisar correo y planificar el día", Area.TRABAJO, "Correo y gestión",
                DaysOfWeek.WEEKDAYS, LocalTime.of(9, 0), 30, Priority.MEDIA, today);
        routine(routines, byName, "Daily con el equipo", Area.TRABAJO, "Reuniones",
                DaysOfWeek.WEEKDAYS, LocalTime.of(9, 30), 15, Priority.ALTA, today);
        routine(routines, byName, "Gimnasio", Area.DEPORTE, "Gimnasio",
                0b0010101, LocalTime.of(19, 0), 60, Priority.MEDIA, today);
        routine(routines, byName, "Estiramientos", Area.DEPORTE, "Estiramientos",
                DaysOfWeek.ALL, LocalTime.of(22, 0), 10, Priority.BAJA, today);

        Task report = task(tasks, byName, "Preparar informe trimestral", Area.TRABAJO, "Proyectos", Priority.ALTA,
                today, LocalTime.of(10, 0), 120, today.plusDays(2),
                "Incluir métricas de ventas y comparativa con el trimestre anterior.", Quadrant.HACER_YA,
                List.of(new ChecklistItem(null, "Exportar las ventas del trimestre", true),
                        new ChecklistItem(null, "Comparar con el trimestre anterior", true),
                        new ChecklistItem(null, "Redactar conclusiones", false),
                        new ChecklistItem(null, "Revisar con dirección", false)));
        LocalDateTime nineThirty = today.atTime(9, 35);
        time.record(report.getId(), nineThirty.minusDays(1), nineThirty.minusDays(1).plusMinutes(25), 25,
                TimeSource.POMODORO);
        time.record(report.getId(), nineThirty.minusDays(1).plusMinutes(30), nineThirty.minusDays(1).plusMinutes(55),
                25, TimeSource.POMODORO);

        task(tasks, byName, "Revisar pull requests pendientes", Area.TRABAJO, "Proyectos", Priority.MEDIA,
                today, LocalTime.of(12, 0), 45, null, null, Quadrant.DELEGAR, null);
        task(tasks, byName, "Comprar fruta y pan", Area.VIDA, "Recados", Priority.BAJA,
                today, null, 20, null, null, null, null);
        task(tasks, byName, "Enviar presupuesto al cliente", Area.TRABAJO, "Correo y gestión", Priority.URGENTE,
                today.minusDays(1), null, 30, today, null, Quadrant.HACER_YA, null);
        task(tasks, byName, "Llamar al dentista", Area.VIDA, "Salud", Priority.MEDIA,
                today.plusDays(1), null, 10, today.plusDays(3), null, null, null);
        task(tasks, byName, "Planificar el próximo sprint", Area.TRABAJO, "Reuniones", Priority.ALTA,
                today.plusDays(2), LocalTime.of(11, 0), 60, null, null, Quadrant.PLANIFICAR,
                List.of(new ChecklistItem(null, "Revisar el backlog", false),
                        new ChecklistItem(null, "Estimar historias con el equipo", false)));
        task(tasks, byName, "Correr 5 km", Area.DEPORTE, "Correr", Priority.MEDIA,
                today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)), LocalTime.of(9, 0), 40, null, null,
                null, null);
        task(tasks, byName, "Idea: curso de arquitectura de software", Area.TRABAJO, "Formación", Priority.BAJA,
                null, null, null, null, "Buscar opciones online y presupuesto de formación.", Quadrant.PLANIFICAR,
                null);
        task(tasks, byName, "Ordenar la carpeta de descargas", Area.TRABAJO, "Correo y gestión", Priority.BAJA,
                null, null, 15, null, null, Quadrant.ELIMINAR, null);
        task(tasks, byName, "Responder encuesta interna", Area.TRABAJO, "Correo y gestión", Priority.BAJA,
                today.plusDays(1), null, 10, null, null, null, null);
        task(tasks, byName, "Ordenar el trastero", Area.VIDA, "Casa", Priority.BAJA,
                null, null, 90, null, null, null, null);
        Task done = task(tasks, byName, "Actualizar el CV", Area.VIDA, "Personal", Priority.MEDIA,
                today, null, 30, null, null, null, null);
        tasks.toggleDone(done.getId());

        // Trabajo ya terminado con tiempo real registrado, para "Estimado frente a real" en Estadísticas
        Task review = task(tasks, byName, "Revisar contrato del proveedor", Area.TRABAJO, "Correo y gestión",
                Priority.MEDIA, today, null, 30, null, null, Quadrant.PLANIFICAR, null);
        time.record(review.getId(), today.atTime(8, 0), today.atTime(8, 45), 45, TimeSource.MANUAL);
        tasks.toggleDone(review.getId());
        Task slides = task(tasks, byName, "Preparar diapositivas de la demo", Area.TRABAJO, "Proyectos",
                Priority.ALTA, today, null, 50, null, null, Quadrant.HACER_YA, null);
        time.record(slides.getId(), today.atTime(7, 0), today.atTime(7, 50), 50, TimeSource.POMODORO);
        tasks.toggleDone(slides.getId());
    }

    private static void routine(RoutineService routines, Map<String, Category> categories, String name, Area area,
                                String category, int days, LocalTime time, int minutes, Priority priority,
                                LocalDate start) {
        Routine routine = new Routine(name, area, days, start);
        routine.setCategory(categories.get(category));
        routine.setStartTime(time);
        routine.setDurationMinutes(minutes);
        routine.setPriority(priority);
        routines.save(routine);
    }

    private static Task task(TaskService tasks, Map<String, Category> categories, String title, Area area,
                             String category, Priority priority, LocalDate day, LocalTime time, Integer minutes,
                             LocalDate due, String description, Quadrant quadrant, List<ChecklistItem> checklist) {
        Task task = new Task(title, area);
        task.setCategory(categories.get(category));
        task.setPriority(priority);
        task.setScheduledDate(day);
        task.setStartTime(time);
        task.setEstimatedMinutes(minutes);
        task.setDueDate(due);
        task.setDescription(description);
        task.setQuadrant(quadrant);
        return tasks.save(task, checklist);
    }
}
