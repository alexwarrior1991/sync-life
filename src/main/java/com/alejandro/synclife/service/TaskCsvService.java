package com.alejandro.synclife.service;

import com.alejandro.synclife.io.csv.CsvReader;
import com.alejandro.synclife.io.csv.CsvWriter;
import com.alejandro.synclife.model.ChecklistItem;
import com.alejandro.synclife.model.TaskFilter;
import com.alejandro.synclife.model.entity.Category;
import com.alejandro.synclife.model.entity.Goal;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.Priority;
import com.alejandro.synclife.model.enums.TaskStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Exporta tareas a CSV (para Excel o LibreOffice) e importa tareas desde CSV.
 *
 * <p>La importación va en dos pasos: {@link #preview} lee y valida el fichero sin tocar nada, y
 * {@link #apply} crea las tareas. Solo crea (nunca modifica ni borra), así que se puede repetir sin miedo;
 * con "omitir idénticas" no duplica lo que ya existe (mismo título, área, día y fecha límite).
 */
public class TaskCsvService {

    /** Columnas del CSV. La cabecera se reconoce sin distinguir mayúsculas ni tildes (y con algunos alias). */
    public enum Column {
        TITULO("Título", "titulo", "title", "tarea", "nombre"),
        DESCRIPCION("Descripción", "descripcion", "description", "notas"),
        AREA("Área", "area"),
        CATEGORIA("Categoría", "categoria", "category"),
        ESTADO("Estado", "estado", "status"),
        PRIORIDAD("Prioridad", "prioridad", "priority"),
        FECHA("Fecha programada", "fecha programada", "fecha", "dia", "date"),
        HORA("Hora", "hora", "time"),
        DURACION("Duración (min)", "duracion (min)", "duracion", "estimacion", "minutos", "duration"),
        FECHA_LIMITE("Fecha límite", "fecha limite", "vence", "due"),
        COMPLETADA("Completada el", "completada el", "completada", "completed"),
        URGENTE("Urgente", "urgente", "urgent"),
        IMPORTANTE("Importante", "importante", "important"),
        OBJETIVO("Objetivo", "objetivo", "goal"),
        RECORDATORIO("Recordatorio", "recordatorio", "aviso", "reminder"),
        SUBTAREAS("Subtareas", "subtareas", "lista de comprobacion", "checklist"),
        TIEMPO("Tiempo registrado (min)", "tiempo registrado (min)", "tiempo registrado", "tiempo real"),
        RUTINA("Rutina", "rutina", "routine");

        private final String header;
        private final Set<String> aliases;

        Column(String header, String... aliases) {
            this.header = header;
            this.aliases = Set.of(aliases);
        }

        public String header() {
            return header;
        }

        /** Solo se exportan: el tiempo registrado y la rutina no se recrean al importar. */
        public boolean isExportOnly() {
            return this == TIEMPO || this == RUTINA;
        }

        static Column fromHeader(String header) {
            String key = TextSearch.normalize(header);
            for (Column column : values()) {
                if (column.aliases.contains(key)) {
                    return column;
                }
            }
            return null;
        }
    }

    public record ImportOptions(boolean createMissingCategories, boolean skipIdentical) {
    }

    /** Un problema en una línea del fichero. */
    public record Issue(int line, String message) {
        @Override
        public String toString() {
            return line > 0 ? "Línea " + line + ": " + message : message;
        }
    }

    /** Una fila válida, lista para crear (la categoría y el objetivo se buscan al aplicar). */
    public record ImportRow(int line, Task task, String categoryName, String goalTitle, List<ChecklistItem> checklist) {
    }

    public record Preview(String charset, char separator, List<Column> columns, List<String> unknownHeaders,
                          List<ImportRow> rows, List<Issue> errors, List<Issue> warnings) {
    }

    public record ImportResult(int created, int skippedIdentical, int categoriesCreated, List<Issue> issues) {
    }

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"), DateTimeFormatter.ofPattern("d.M.yyyy"));
    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm[:ss]"), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'H:mm[:ss]"),
            DateTimeFormatter.ofPattern("d/M/yyyy H:mm[:ss]"), DateTimeFormatter.ofPattern("d-M-yyyy H:mm[:ss]"));
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");

    private final TaskService tasks;
    private final CategoryService categories;
    private final GoalService goals;

    public TaskCsvService(TaskService tasks, CategoryService categories, GoalService goals) {
        this.tasks = tasks;
        this.categories = categories;
        this.goals = goals;
    }

    // ---------------------------------------------------------------- exportar

    /** Todas las tareas (incluidas las de rutinas, marcadas en la columna "Rutina"). */
    public byte[] exportAll() {
        return export(tasks.search(TaskFilter.all()));
    }

    public byte[] export(List<Task> list) {
        Map<Long, List<ChecklistItem>> checklists = tasks.checklists(list.stream().map(Task::getId).toList());
        List<List<String>> rows = new ArrayList<>();
        List<String> header = new ArrayList<>();
        for (Column column : Column.values()) {
            header.add(column.header());
        }
        rows.add(header);
        for (Task task : list) {
            Map<Column, String> cells = new EnumMap<>(Column.class);
            cells.put(Column.TITULO, task.getTitle());
            cells.put(Column.DESCRIPCION, task.getDescription());
            cells.put(Column.AREA, task.getArea().getDisplayName());
            cells.put(Column.CATEGORIA, task.getCategory() == null ? null : task.getCategory().getName());
            cells.put(Column.ESTADO, task.getStatus().getDisplayName());
            cells.put(Column.PRIORIDAD, task.getPriority().getDisplayName());
            cells.put(Column.FECHA, date(task.getScheduledDate()));
            cells.put(Column.HORA, task.getStartTime() == null ? null : TIME.format(task.getStartTime()));
            cells.put(Column.DURACION, task.getEstimatedMinutes() == null ? null : task.getEstimatedMinutes().toString());
            cells.put(Column.FECHA_LIMITE, date(task.getDueDate()));
            cells.put(Column.COMPLETADA, dateTime(task.getCompletedAt()));
            cells.put(Column.URGENTE, bool(task.getUrgent()));
            cells.put(Column.IMPORTANTE, bool(task.getImportant()));
            cells.put(Column.OBJETIVO, task.getGoal() == null ? null : task.getGoal().getTitle());
            cells.put(Column.RECORDATORIO, dateTime(task.getRemindAt()));
            cells.put(Column.SUBTAREAS, checklistText(checklists.getOrDefault(task.getId(), List.of())));
            cells.put(Column.TIEMPO, task.getTrackedMinutes() == 0 ? null : Integer.toString(task.getTrackedMinutes()));
            cells.put(Column.RUTINA, task.getRoutine() == null ? null : task.getRoutine().getName());
            List<String> row = new ArrayList<>();
            for (Column column : Column.values()) {
                row.add(cells.get(column));
            }
            rows.add(row);
        }
        return CsvWriter.write(rows, CsvWriter.DEFAULT_SEPARATOR);
    }

    // ---------------------------------------------------------------- importar: vista previa

    /** Lee y valida el fichero sin guardar nada. */
    public Preview preview(byte[] content) {
        CsvReader.Parsed parsed = CsvReader.parse(content);
        List<Issue> errors = new ArrayList<>();
        List<Issue> warnings = new ArrayList<>();
        if (parsed.rows().isEmpty()) {
            errors.add(new Issue(0, "El fichero está vacío."));
            return new Preview(parsed.charset().name(), parsed.separator(), List.of(), List.of(), List.of(), errors,
                    warnings);
        }
        CsvReader.Row header = parsed.rows().get(0);
        Map<Column, Integer> index = new EnumMap<>(Column.class);
        List<String> unknown = new ArrayList<>();
        for (int i = 0; i < header.cells().size(); i++) {
            String name = CsvReader.unprotect(header.cell(i)).replace("﻿", "").trim();
            Column column = Column.fromHeader(name);
            if (column == null) {
                if (!name.isEmpty()) {
                    unknown.add(name);
                }
            } else if (!index.containsKey(column)) {
                index.put(column, i);
            }
        }
        if (!index.containsKey(Column.TITULO)) {
            errors.add(new Issue(header.line(), "Falta la columna «Título» en la cabecera."));
            return new Preview(parsed.charset().name(), parsed.separator(), List.copyOf(index.keySet()), unknown,
                    List.of(), errors, warnings);
        }
        List<ImportRow> rows = new ArrayList<>();
        for (CsvReader.Row row : parsed.rows().subList(1, parsed.rows().size())) {
            Function<Column, String> cell = column -> {
                Integer i = index.get(column);
                return i == null ? "" : CsvReader.unprotect(row.cell(i)).trim();
            };
            try {
                rows.add(parseRow(row.line(), cell, warnings));
            } catch (IllegalArgumentException e) {
                errors.add(new Issue(row.line(), e.getMessage()));
            }
        }
        return new Preview(parsed.charset().name(), parsed.separator(), List.copyOf(index.keySet()), unknown, rows,
                errors, warnings);
    }

    private ImportRow parseRow(int line, Function<Column, String> cell, List<Issue> warnings) {
        String title = cell.apply(Column.TITULO);
        if (title.isEmpty()) {
            throw new IllegalArgumentException("sin título");
        }
        if (title.length() > 200) {
            throw new IllegalArgumentException("el título supera 200 caracteres");
        }
        String areaText = cell.apply(Column.AREA);
        Area area = parseArea(areaText);
        if (area == null) {
            if (!areaText.isEmpty()) {
                warnings.add(new Issue(line, "no se reconoce el área «" + areaText + "»: se usa Trabajo"));
            }
            area = Area.TRABAJO;
        }
        Task task = new Task(title, area);
        String description = cell.apply(Column.DESCRIPCION);
        task.setDescription(description.isEmpty() ? null : description);
        task.setStatus(parseStatus(cell.apply(Column.ESTADO), line, warnings));
        task.setPriority(parsePriority(cell.apply(Column.PRIORIDAD), line, warnings));
        task.setScheduledDate(parseDate(cell.apply(Column.FECHA), "fecha programada"));
        task.setStartTime(parseTime(cell.apply(Column.HORA)));
        task.setEstimatedMinutes(parseMinutes(cell.apply(Column.DURACION), line, warnings));
        task.setDueDate(parseDate(cell.apply(Column.FECHA_LIMITE), "fecha límite"));
        LocalDateTime completed = parseDateTime(cell.apply(Column.COMPLETADA), "fecha de completado");
        if (task.getStatus() == TaskStatus.COMPLETADA) {
            task.setCompletedAt(completed);
        }
        task.setUrgent(parseBoolean(cell.apply(Column.URGENTE)));
        task.setImportant(parseBoolean(cell.apply(Column.IMPORTANTE)));
        task.setRemindAt(parseDateTime(cell.apply(Column.RECORDATORIO), "recordatorio"));
        List<ChecklistItem> checklist = parseChecklist(cell.apply(Column.SUBTAREAS));
        if (checklist.size() > TaskService.MAX_CHECKLIST_ITEMS) {
            throw new IllegalArgumentException("más de " + TaskService.MAX_CHECKLIST_ITEMS + " subtareas");
        }
        String category = cell.apply(Column.CATEGORIA);
        String goal = cell.apply(Column.OBJETIVO);
        return new ImportRow(line, task, category.isEmpty() ? null : category, goal.isEmpty() ? null : goal, checklist);
    }

    // ---------------------------------------------------------------- importar: aplicar

    /** Crea las tareas válidas de la vista previa. */
    public ImportResult apply(Preview preview, ImportOptions options) {
        List<Issue> issues = new ArrayList<>();
        Map<String, Category> categoryByKey = new HashMap<>();
        for (Category category : categories.findAll(true)) {
            categoryByKey.put(key(category.getArea(), category.getName()), category);
        }
        Map<String, Goal> goalByTitle = goals.findAll(true).stream().map(GoalService.GoalProgress::goal)
                .collect(Collectors.toMap(g -> TextSearch.normalize(g.getTitle()), g -> g, (a, b) -> a));
        Set<String> existing = new HashSet<>();
        if (options.skipIdentical()) {
            for (Task task : tasks.search(TaskFilter.all())) {
                existing.add(identity(task));
            }
        }
        int created = 0;
        int skipped = 0;
        int categoriesCreated = 0;
        for (ImportRow row : preview.rows()) {
            Task task = copyOf(row.task());
            if (options.skipIdentical() && !existing.add(identity(task))) {
                skipped++;
                continue;
            }
            if (row.categoryName() != null) {
                Category category = categoryByKey.get(key(task.getArea(), row.categoryName()));
                if (category == null && options.createMissingCategories()) {
                    try {
                        category = categories.save(new Category(row.categoryName(), task.getArea(),
                                task.getArea().getColorHex()));
                        categoryByKey.put(key(category.getArea(), category.getName()), category);
                        categoriesCreated++;
                    } catch (ValidationException e) {
                        issues.add(new Issue(row.line(), "no se pudo crear la categoría: " + e.getMessage()));
                    }
                }
                if (category == null && !options.createMissingCategories()) {
                    issues.add(new Issue(row.line(), "la categoría «" + row.categoryName()
                            + "» no existe en " + task.getArea().getDisplayName() + ": se importa sin categoría"));
                }
                task.setCategory(category);
            }
            if (row.goalTitle() != null) {
                Goal goal = goalByTitle.get(TextSearch.normalize(row.goalTitle()));
                if (goal == null) {
                    issues.add(new Issue(row.line(), "el objetivo «" + row.goalTitle()
                            + "» no existe: se importa sin objetivo"));
                }
                task.setGoal(goal);
            }
            try {
                tasks.save(task, row.checklist());
                created++;
            } catch (ValidationException e) {
                issues.add(new Issue(row.line(), e.getMessage()));
            }
        }
        return new ImportResult(created, skipped, categoriesCreated, issues);
    }

    /** Lo que hace "idéntica" a una tarea: título (sin tildes ni mayúsculas), área, día y fecha límite. */
    private static String identity(Task task) {
        return TextSearch.normalize(task.getTitle()) + '|' + task.getArea() + '|' + task.getScheduledDate()
                + '|' + task.getDueDate();
    }

    private static String key(Area area, String categoryName) {
        return area + "|" + TextSearch.normalize(categoryName);
    }

    /** Una tarea nueva con los datos de la fila (la vista previa se puede aplicar más de una vez). */
    private static Task copyOf(Task source) {
        Task task = new Task(source.getTitle(), source.getArea());
        task.setDescription(source.getDescription());
        task.setStatus(source.getStatus());
        task.setPriority(source.getPriority());
        task.setScheduledDate(source.getScheduledDate());
        task.setStartTime(source.getStartTime());
        task.setEstimatedMinutes(source.getEstimatedMinutes());
        task.setDueDate(source.getDueDate());
        task.setCompletedAt(source.getCompletedAt());
        task.setUrgent(source.getUrgent());
        task.setImportant(source.getImportant());
        task.setRemindAt(source.getRemindAt());
        return task;
    }

    // ---------------------------------------------------------------- formatos

    private static String date(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static String dateTime(LocalDateTime value) {
        return value == null ? null : DATE_TIME.format(value);
    }

    private static String bool(Boolean value) {
        return value == null ? null : value ? "Sí" : "No";
    }

    private static String checklistText(List<ChecklistItem> items) {
        return items.isEmpty() ? null
                : items.stream().map(i -> (i.done() ? "[x] " : "[ ] ") + i.title()).collect(Collectors.joining("\n"));
    }

    private static Area parseArea(String text) {
        String key = TextSearch.normalize(text);
        for (Area area : Area.values()) {
            if (key.equals(TextSearch.normalize(area.getDisplayName())) || key.equals(area.name().toLowerCase())) {
                return area;
            }
        }
        return null;
    }

    private static TaskStatus parseStatus(String text, int line, List<Issue> warnings) {
        String key = TextSearch.normalize(text);
        if (key.equals("hecha") || key.equals("hecho") || key.equals("done")) {
            return TaskStatus.COMPLETADA;
        }
        return parseEnum(text, TaskStatus.values(), TaskStatus::getDisplayName, TaskStatus.PENDIENTE, "el estado",
                line, warnings);
    }

    private static Priority parsePriority(String text, int line, List<Issue> warnings) {
        if (text.matches("[1-4]")) {
            return Priority.fromWeight(Integer.parseInt(text));
        }
        return parseEnum(text, Priority.values(), Priority::getDisplayName, Priority.MEDIA, "la prioridad", line,
                warnings);
    }

    private static <E extends Enum<E>> E parseEnum(String text, E[] values, Function<E, String> display, E fallback,
                                                  String what, int line, List<Issue> warnings) {
        if (text.isEmpty()) {
            return fallback;
        }
        String key = TextSearch.normalize(text).replace('_', ' ');
        for (E value : values) {
            if (key.equals(TextSearch.normalize(display.apply(value)))
                    || key.equals(value.name().toLowerCase().replace('_', ' '))) {
                return value;
            }
        }
        warnings.add(new Issue(line, "no se reconoce " + what + " «" + text + "»: se usa " + display.apply(fallback)));
        return fallback;
    }

    private static LocalDate parseDate(String text, String what) {
        if (text.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(text, format);
            } catch (DateTimeParseException ignored) {
                // se prueba el siguiente formato
            }
        }
        throw new IllegalArgumentException(what + " no válida: «" + text + "» (usa 2026-10-12 o 12/10/2026)");
    }

    private static LocalDateTime parseDateTime(String text, String what) {
        if (text.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter format : DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(text, format);
            } catch (DateTimeParseException ignored) {
                // se prueba el siguiente formato
            }
        }
        throw new IllegalArgumentException(what + " no válido: «" + text + "» (usa 2026-10-12 09:30)");
    }

    private static LocalTime parseTime(String text) {
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalTime.parse(text.length() > 5 ? text.substring(0, 5) : text, TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("hora no válida: «" + text + "» (usa 09:30)");
        }
    }

    private static Integer parseMinutes(String text, int line, List<Issue> warnings) {
        if (text.isEmpty()) {
            return null;
        }
        try {
            int minutes = Integer.parseInt(text);
            if (minutes > 0 && minutes <= 24 * 60) {
                return minutes;
            }
        } catch (NumberFormatException ignored) {
            // aviso abajo
        }
        warnings.add(new Issue(line, "duración «" + text + "» no válida (minutos entre 1 y 1440): se deja vacía"));
        return null;
    }

    private static Boolean parseBoolean(String text) {
        String key = TextSearch.normalize(text);
        return switch (key) {
            case "" -> null;
            case "si", "s", "x", "1", "true", "yes", "y" -> true;
            default -> false;
        };
    }

    private static List<ChecklistItem> parseChecklist(String text) {
        List<ChecklistItem> items = new ArrayList<>();
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            boolean done = false;
            if (line.startsWith("[x]") || line.startsWith("[X]")) {
                done = true;
                line = line.substring(3).trim();
            } else if (line.startsWith("[ ]") || line.startsWith("[]")) {
                line = line.substring(line.indexOf(']') + 1).trim();
            } else if (line.startsWith("- ")) {
                line = line.substring(2).trim();
            }
            if (!line.isEmpty()) {
                items.add(new ChecklistItem(null, line.length() > 200 ? line.substring(0, 200) : line, done));
            }
        }
        return items;
    }
}
