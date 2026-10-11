package com.alejandro.synclife.service;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.TestSupport;
import com.alejandro.synclife.model.ChecklistItem;
import com.alejandro.synclife.model.TaskFilter;
import com.alejandro.synclife.model.entity.Category;
import com.alejandro.synclife.model.entity.Goal;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.Priority;
import com.alejandro.synclife.model.enums.Quadrant;
import com.alejandro.synclife.model.enums.TaskStatus;
import com.alejandro.synclife.service.TaskCsvService.ImportOptions;
import com.alejandro.synclife.service.TaskCsvService.ImportResult;
import com.alejandro.synclife.service.TaskCsvService.Preview;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static com.alejandro.synclife.TestSupport.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

class TaskCsvServiceTest {

    private static final ImportOptions DEFAULTS = new ImportOptions(true, true);

    @TempDir
    Path dir;
    final List<AppContext> contexts = new ArrayList<>();

    @AfterEach
    void tearDown() {
        contexts.forEach(AppContext::close);
    }

    private AppContext open(String name) {
        AppContext ctx = TestSupport.context(dir.resolve(name));
        contexts.add(ctx);
        return ctx;
    }

    private static Category category(AppContext ctx, String name) {
        return ctx.categories().findAll(true).stream().filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void exportingAndImportingIntoAnotherDatabaseKeepsTheTask() {
        AppContext source = open("origen");
        Goal goal = source.goals().save(new Goal("Lanzar la 2.0", Area.TRABAJO));
        Task task = new Task("Informe; versión \"final\"", Area.TRABAJO);
        task.setDescription("Línea 1\nLínea 2");
        task.setCategory(category(source, "Proyectos"));
        task.setPriority(Priority.ALTA);
        task.setScheduledDate(TODAY.plusDays(1));
        task.setStartTime(LocalTime.of(10, 0));
        task.setEstimatedMinutes(90);
        task.setDueDate(TODAY.plusDays(3));
        task.setQuadrant(Quadrant.PLANIFICAR);
        task.setGoal(goal);
        task.setRemindAt(TODAY.plusDays(1).atTime(9, 45));
        source.tasks().save(task, List.of(new ChecklistItem(null, "Datos", true), new ChecklistItem(null, "Gráficos", false)));
        byte[] csv = source.csv().exportAll();

        AppContext target = open("destino");
        target.goals().save(new Goal("lanzar la 2.0", Area.TRABAJO)); // se empareja sin distinguir mayúsculas
        Preview preview = target.csv().preview(csv);
        assertThat(preview.errors()).isEmpty();
        ImportResult result = target.csv().apply(preview, DEFAULTS);

        assertThat(result.created()).isEqualTo(1);
        Task imported = target.tasks().search(TaskFilter.all()).get(0);
        assertThat(imported.getTitle()).isEqualTo("Informe; versión \"final\"");
        assertThat(imported.getDescription()).isEqualTo("Línea 1\nLínea 2");
        assertThat(imported.getCategory().getName()).isEqualTo("Proyectos");
        assertThat(imported.getPriority()).isEqualTo(Priority.ALTA);
        assertThat(imported.getScheduledDate()).isEqualTo(TODAY.plusDays(1));
        assertThat(imported.getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(imported.getEstimatedMinutes()).isEqualTo(90);
        assertThat(imported.getDueDate()).isEqualTo(TODAY.plusDays(3));
        assertThat(imported.getQuadrant()).isEqualTo(Quadrant.PLANIFICAR);
        assertThat(imported.getGoal().getTitle()).isEqualTo("lanzar la 2.0");
        assertThat(imported.getRemindAt()).isEqualTo(TODAY.plusDays(1).atTime(9, 45));
        assertThat(target.tasks().checklist(imported.getId())).extracting(ChecklistItem::title, ChecklistItem::done)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Datos", true),
                        org.assertj.core.groups.Tuple.tuple("Gráficos", false));
    }

    @Test
    void importingTheSameFileTwiceDoesNotDuplicate() {
        AppContext ctx = open("una");
        Task task = new Task("Comprar pan", Area.VIDA);
        task.setScheduledDate(TODAY);
        ctx.tasks().save(task);
        byte[] csv = ctx.csv().exportAll();

        ImportResult result = ctx.csv().apply(ctx.csv().preview(csv), DEFAULTS);

        assertThat(result.created()).isZero();
        assertThat(result.skippedIdentical()).isEqualTo(1);
        assertThat(ctx.tasks().search(TaskFilter.all())).hasSize(1);
        // Sin "omitir idénticas" sí se crea otra
        assertThat(ctx.csv().apply(ctx.csv().preview(csv), new ImportOptions(true, false)).created()).isEqualTo(1);
    }

    @Test
    void readsASpreadsheetWrittenByHand() {
        AppContext ctx = open("excel");
        String text = """
                titulo,area,categoria,estado,prioridad,fecha,hora,duracion,urgente,importante,subtareas
                Llamar al banco,Vida,salud,Pendiente,3,12/10/2026,9:30,15,sí,no,"[x] buscar número
                [ ] llamar"
                Pedir cita,vida,Médico nuevo,hecha,Urgente,2026-10-13,,,,,
                """;

        Preview preview = ctx.csv().preview(text.getBytes(StandardCharsets.UTF_8));
        assertThat(preview.errors()).isEmpty();
        assertThat(preview.separator()).isEqualTo(',');
        ImportResult result = ctx.csv().apply(preview, new ImportOptions(true, true));

        assertThat(result.created()).isEqualTo(2);
        assertThat(result.categoriesCreated()).isEqualTo(1); // "Médico nuevo"; "salud" ya existía como "Salud"
        List<Task> tasks = ctx.tasks().search(TaskFilter.all());
        Task bank = tasks.stream().filter(t -> t.getTitle().equals("Llamar al banco")).findFirst().orElseThrow();
        assertThat(bank.getCategory().getName()).isEqualTo("Salud");
        assertThat(bank.getPriority()).isEqualTo(Priority.ALTA);
        assertThat(bank.getScheduledDate()).isEqualTo(TODAY);
        assertThat(bank.getStartTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(bank.getQuadrant()).isEqualTo(Quadrant.DELEGAR);
        assertThat(bank.getSubtaskTotal()).isEqualTo(2);
        Task appointment = tasks.stream().filter(t -> t.getTitle().equals("Pedir cita")).findFirst().orElseThrow();
        assertThat(appointment.getStatus()).isEqualTo(TaskStatus.COMPLETADA);
        assertThat(appointment.getCompletedAt()).isNotNull();
        assertThat(appointment.getCategory().getName()).isEqualTo("Médico nuevo");
    }

    @Test
    void invalidRowsAreReportedWithTheirLineAndTheRestIsImported() {
        AppContext ctx = open("errores");
        String text = """
                Título;Fecha programada;Hora;Duración (min);Área;Columna rara
                Buena;2026-10-12;10:00;30;Trabajo;x
                ;2026-10-12;;;;
                Fecha mala;32/13/2026;;;;
                Duración mala;;;muchos;Ocio;
                """;

        Preview preview = ctx.csv().preview(text.getBytes(StandardCharsets.UTF_8));

        assertThat(preview.unknownHeaders()).containsExactly("Columna rara");
        assertThat(preview.errors()).extracting(TaskCsvService.Issue::line).containsExactly(3, 4);
        assertThat(preview.warnings()).extracting(TaskCsvService.Issue::line).containsExactly(5, 5);
        assertThat(preview.rows()).hasSize(2);
        assertThat(ctx.csv().apply(preview, DEFAULTS).created()).isEqualTo(2);
    }

    @Test
    void missingCategoriesCanBeLeftOutInsteadOfCreated() {
        AppContext ctx = open("sin-crear");
        String text = "Título;Área;Categoría\nTarea;Trabajo;Inventada\n";

        ImportResult result = ctx.csv().apply(ctx.csv().preview(text.getBytes(StandardCharsets.UTF_8)),
                new ImportOptions(false, true));

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.categoriesCreated()).isZero();
        assertThat(result.issues()).singleElement().asString().contains("Inventada");
        assertThat(ctx.tasks().search(TaskFilter.all()).get(0).getCategory()).isNull();
    }

    @Test
    void withoutATitleColumnNothingIsImported() {
        AppContext ctx = open("sin-titulo");
        Preview preview = ctx.csv().preview("Nombre raro;Área\nx;Vida\n".getBytes(Charset.forName("windows-1252")));
        assertThat(preview.errors()).singleElement().asString().contains("Título");
        assertThat(preview.rows()).isEmpty();
    }

    @Test
    void exportMarksRoutineTasksAndTrackedTime() {
        AppContext ctx = open("rutinas");
        ctx.routines().save(new com.alejandro.synclife.model.entity.Routine("Daily", Area.TRABAJO,
                com.alejandro.synclife.model.DaysOfWeek.ALL, TODAY));
        Task instance = ctx.tasks().tasksForDay(TODAY).get(0);
        ctx.timeTracking().addManual(instance.getId(), 15);

        String text = new String(ctx.csv().export(List.of(ctx.tasks().findById(instance.getId()).orElseThrow())),
                StandardCharsets.UTF_8);

        String row = text.lines().skip(1).findFirst().orElseThrow();
        assertThat(row).endsWith(";15;Daily");
    }
}
