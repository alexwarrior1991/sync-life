package com.alejandro.synclife.service;

import com.alejandro.synclife.model.DaysOfWeek;
import com.alejandro.synclife.model.entity.Category;
import com.alejandro.synclife.model.entity.Routine;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.Priority;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Datos de ejemplo para probar la aplicación (Ajustes → "Cargar datos de ejemplo"). */
public final class DemoData {

    private DemoData() {
    }

    public static void seed(TaskService tasks, CategoryService categories, RoutineService routines) {
        LocalDate today = tasks.today();
        Map<String, Category> byName = categories.findAll(true).stream()
                .collect(Collectors.toMap(Category::getName, Function.identity(), (a, b) -> a));

        routine(routines, byName, "Revisar correo y planificar el día", Area.TRABAJO, "Correo y gestión",
                DaysOfWeek.WEEKDAYS, LocalTime.of(9, 0), 30, Priority.MEDIA, today);
        routine(routines, byName, "Daily con el equipo", Area.TRABAJO, "Reuniones",
                DaysOfWeek.WEEKDAYS, LocalTime.of(9, 30), 15, Priority.ALTA, today);
        routine(routines, byName, "Gimnasio", Area.DEPORTE, "Gimnasio",
                0b0010101, LocalTime.of(19, 0), 60, Priority.MEDIA, today);
        routine(routines, byName, "Estiramientos", Area.DEPORTE, "Estiramientos",
                DaysOfWeek.ALL, LocalTime.of(22, 0), 10, Priority.BAJA, today);

        task(tasks, byName, "Preparar informe trimestral", Area.TRABAJO, "Proyectos", Priority.ALTA,
                today, LocalTime.of(10, 0), 120, today.plusDays(2),
                "Incluir métricas de ventas y comparativa con el trimestre anterior.");
        task(tasks, byName, "Revisar pull requests pendientes", Area.TRABAJO, "Proyectos", Priority.MEDIA,
                today, LocalTime.of(12, 0), 45, null, null);
        task(tasks, byName, "Comprar fruta y pan", Area.VIDA, "Recados", Priority.BAJA,
                today, null, 20, null, null);
        task(tasks, byName, "Enviar presupuesto al cliente", Area.TRABAJO, "Correo y gestión", Priority.URGENTE,
                today.minusDays(1), null, 30, today, null);
        task(tasks, byName, "Llamar al dentista", Area.VIDA, "Salud", Priority.MEDIA,
                today.plusDays(1), null, 10, today.plusDays(3), null);
        task(tasks, byName, "Planificar el próximo sprint", Area.TRABAJO, "Reuniones", Priority.ALTA,
                today.plusDays(2), LocalTime.of(11, 0), 60, null, null);
        task(tasks, byName, "Correr 5 km", Area.DEPORTE, "Correr", Priority.MEDIA,
                today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)), LocalTime.of(9, 0), 40, null, null);
        task(tasks, byName, "Idea: curso de arquitectura de software", Area.TRABAJO, "Formación", Priority.BAJA,
                null, null, null, null, "Buscar opciones online y presupuesto de formación.");
        task(tasks, byName, "Ordenar el trastero", Area.VIDA, "Casa", Priority.BAJA,
                null, null, 90, null, null);
        Task done = task(tasks, byName, "Actualizar el CV", Area.VIDA, "Personal", Priority.MEDIA,
                today, null, 30, null, null);
        tasks.toggleDone(done.getId());
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
                             LocalDate due, String description) {
        Task task = new Task(title, area);
        task.setCategory(categories.get(category));
        task.setPriority(priority);
        task.setScheduledDate(day);
        task.setStartTime(time);
        task.setEstimatedMinutes(minutes);
        task.setDueDate(due);
        task.setDescription(description);
        return tasks.save(task);
    }
}
