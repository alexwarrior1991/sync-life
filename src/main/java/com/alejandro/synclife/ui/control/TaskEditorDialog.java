package com.alejandro.synclife.ui.control;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.controller.TaskEditorController;
import com.alejandro.synclife.model.entity.Category;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.ui.Dialogs;
import com.alejandro.synclife.ui.FxAsync;
import com.alejandro.synclife.ui.Theme;
import com.alejandro.synclife.ui.ViewLoader;
import javafx.application.Platform;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.Window;

import java.time.LocalDate;
import java.util.List;

/** Diálogo modal para crear o editar una tarea. */
public final class TaskEditorDialog {

    private TaskEditorDialog() {
    }

    /**
     * Carga las categorías, muestra el diálogo y, si el usuario guarda, persiste la tarea.
     * {@code refresh} se llama después de guardar (o de mostrar un error) para recargar la pantalla.
     */
    public static void open(Window owner, AppContext context, Task task, LocalDate defaultDate,
                            Runnable refresh) {
        FxAsync.run(() -> context.categories().findAll(false), categories ->
                create(owner, context, task, defaultDate, categories).showAndWait().ifPresent(edited ->
                        FxAsync.run(() -> context.tasks().save(edited), saved -> refresh.run(), error -> {
                            Dialogs.error(error);
                            refresh.run();
                        })));
    }

    /** Crea el diálogo sin mostrarlo. */
    public static Dialog<Task> create(Window owner, AppContext context, Task task, LocalDate defaultDate,
                                      List<Category> categories) {
        ViewLoader.Loaded<TaskEditorController> loaded = ViewLoader.load(context, "task-editor.fxml");
        TaskEditorController form = loaded.controller();
        form.setCategories(categories);
        form.edit(task, defaultDate);

        Dialog<Task> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle(task == null ? "Nueva tarea" : "Editar tarea");
        dialog.setHeaderText(task == null ? "Nueva tarea" : "Editar tarea");
        ButtonType save = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(loaded.root());
        dialog.getDialogPane().lookupButton(save).disableProperty().bind(form.invalidProperty());
        Theme.style(dialog.getDialogPane());
        dialog.setResultConverter(button -> button == save ? form.applyTo(task == null ? new Task() : task) : null);
        dialog.setOnShown(e -> Platform.runLater(form::focusTitle));
        return dialog;
    }
}
