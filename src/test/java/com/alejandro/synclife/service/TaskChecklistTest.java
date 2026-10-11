package com.alejandro.synclife.service;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.TestSupport;
import com.alejandro.synclife.model.ChecklistItem;
import com.alejandro.synclife.model.DaysOfWeek;
import com.alejandro.synclife.model.entity.Routine;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Area;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.alejandro.synclife.TestSupport.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class TaskChecklistTest {

    @TempDir
    Path dir;
    AppContext ctx;
    TaskService tasks;

    @BeforeEach
    void setUp() {
        ctx = TestSupport.context(dir);
        tasks = ctx.tasks();
    }

    @AfterEach
    void tearDown() {
        ctx.close();
    }

    private static ChecklistItem item(String title, boolean done) {
        return new ChecklistItem(null, title, done);
    }

    @Test
    void savingWithAChecklistStoresItInOrderAndUpdatesTheCounters() {
        Task saved = tasks.save(new Task("Informe", Area.TRABAJO),
                List.of(item("Datos", true), item("Gráficos", false), item("Revisión", false)));

        assertThat(tasks.checklist(saved.getId())).extracting(ChecklistItem::title, ChecklistItem::done)
                .containsExactly(tuple("Datos", true), tuple("Gráficos", false), tuple("Revisión", false));
        // Los contadores (@Formula) vienen ya calculados en la tarea devuelta y al volver a leerla
        assertThat(saved.getSubtaskTotal()).isEqualTo(3);
        assertThat(saved.getSubtaskDone()).isEqualTo(1);
        Task reloaded = tasks.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getSubtaskTotal()).isEqualTo(3);
        assertThat(reloaded.getSubtaskDone()).isEqualTo(1);
    }

    @Test
    void editingTheChecklistUpdatesReordersAddsAndRemovesItems() {
        Task saved = tasks.save(new Task("Informe", Area.TRABAJO),
                List.of(item("Uno", false), item("Dos", false), item("Tres", false)));
        List<ChecklistItem> current = tasks.checklist(saved.getId());
        long idOne = current.get(0).id();
        long idThree = current.get(2).id();

        // "Tres" sube al principio y se marca, "Uno" cambia de texto, "Dos" desaparece y hay uno nuevo
        List<ChecklistItem> edited = List.of(
                current.get(2).withDone(true),
                current.get(0).withTitle("Uno bis"),
                item("Cuatro", false));
        Task again = tasks.save(tasks.findById(saved.getId()).orElseThrow(), edited);

        List<ChecklistItem> after = tasks.checklist(saved.getId());
        assertThat(after).extracting(ChecklistItem::title).containsExactly("Tres", "Uno bis", "Cuatro");
        assertThat(after.get(0).id()).isEqualTo(idThree);
        assertThat(after.get(0).done()).isTrue();
        assertThat(after.get(1).id()).isEqualTo(idOne);
        assertThat(again.getSubtaskTotal()).isEqualTo(3);
        assertThat(again.getSubtaskDone()).isEqualTo(1);
        assertThat(TestSupport.single(ctx, "select count(*) from subtask")).isEqualTo("3");
    }

    @Test
    void savingWithoutAChecklistLeavesItUntouched() {
        Task saved = tasks.save(new Task("Informe", Area.TRABAJO), List.of(item("Uno", false)));
        Task loaded = tasks.findById(saved.getId()).orElseThrow();
        loaded.setTitle("Informe final");
        tasks.save(loaded);
        assertThat(tasks.checklist(saved.getId())).extracting(ChecklistItem::title).containsExactly("Uno");
    }

    @Test
    void blankItemsAreIgnoredAndTitlesTrimmed() {
        Task saved = tasks.save(new Task("Informe", Area.TRABAJO),
                List.of(item("  Datos  ", false), item("   ", false), item(null, true)));
        assertThat(tasks.checklist(saved.getId())).extracting(ChecklistItem::title).containsExactly("Datos");
    }

    @Test
    void limitsAreValidated() {
        List<ChecklistItem> tooMany = new ArrayList<>();
        for (int i = 0; i <= TaskService.MAX_CHECKLIST_ITEMS; i++) {
            tooMany.add(item("Paso " + i, false));
        }
        assertThatThrownBy(() -> tasks.save(new Task("Muchos", Area.TRABAJO), tooMany))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> tasks.save(new Task("Largo", Area.TRABAJO), List.of(item("x".repeat(201), false))))
                .isInstanceOf(ValidationException.class);
        assertThat(TestSupport.single(ctx, "select count(*) from task")).isEqualTo("0");
    }

    @Test
    void routineInstancesCannotHaveAChecklist() {
        ctx.routines().save(new Routine("Gimnasio", Area.DEPORTE, DaysOfWeek.ALL, TODAY));
        Task instance = tasks.tasksForDay(TODAY).get(0);

        assertThatThrownBy(() -> tasks.save(instance, List.of(item("Calentar", false))))
                .isInstanceOf(ValidationException.class)
                .hasMessage(TaskService.ROUTINE_CHECKLIST_ERROR);
        // Una lista vacía sí se acepta (es lo que envía el editor para una tarea de rutina)
        assertThat(tasks.save(instance, List.of()).getSubtaskTotal()).isZero();
    }

    @Test
    void anIdFromAnotherTaskIsTreatedAsANewItem() {
        Task first = tasks.save(new Task("Primera", Area.TRABAJO), List.of(item("Ajeno", false)));
        long foreignId = tasks.checklist(first.getId()).get(0).id();

        Task second = tasks.save(new Task("Segunda", Area.TRABAJO),
                List.of(new ChecklistItem(foreignId, "Copiado", false)));

        assertThat(tasks.checklist(first.getId())).extracting(ChecklistItem::title).containsExactly("Ajeno");
        assertThat(tasks.checklist(second.getId())).extracting(ChecklistItem::title).containsExactly("Copiado");
    }
}
