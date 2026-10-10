package com.alejandro.synclife.ui;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.ui.control.TaskEditorDialog;
import javafx.scene.Node;
import javafx.stage.Window;

import java.time.LocalDate;
import java.util.concurrent.Callable;

/**
 * Acciones sobre tareas compartidas por todas las pantallas (completar, editar, mover, omitir,
 * borrar). Tras cada cambio se llama a {@code refresh} para recargar la pantalla.
 */
public class TaskActions {

    private final AppContext context;
    private final Node anchor;
    private final Runnable refresh;

    public TaskActions(AppContext context, Node anchor, Runnable refresh) {
        this.context = context;
        this.anchor = anchor;
        this.refresh = refresh;
    }

    public LocalDate today() {
        return context.today();
    }

    public void toggleDone(Task task) {
        execute(() -> context.tasks().toggleDone(task.getId()));
    }

    public void edit(Task task) {
        TaskEditorDialog.open(window(), context, task, task.getScheduledDate(), refresh);
    }

    public void create(LocalDate date) {
        TaskEditorDialog.open(window(), context, null, date, refresh);
    }

    /** Programa la tarea para {@code date} (null = sin fecha). */
    public void moveTo(Task task, LocalDate date) {
        execute(() -> context.tasks().reschedule(task.getId(), date));
    }

    public void moveTo(long taskId, LocalDate date) {
        execute(() -> context.tasks().reschedule(taskId, date));
    }

    public void skip(Task task) {
        execute(() -> context.tasks().skip(task.getId()));
    }

    public void delete(Task task) {
        if (Dialogs.confirm("¿Eliminar la tarea?",
                "«" + task.getTitle() + "» se eliminará definitivamente.", "Eliminar")) {
            execute(() -> {
                context.tasks().delete(task.getId());
                return null;
            });
        }
    }

    private void execute(Callable<?> work) {
        FxAsync.run(work, ignored -> refresh.run(), error -> {
            Dialogs.error(error);
            refresh.run(); // vuelve a mostrar el estado real tras un error
        });
    }

    private Window window() {
        return anchor.getScene() == null ? null : anchor.getScene().getWindow();
    }
}
