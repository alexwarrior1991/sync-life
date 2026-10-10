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
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDate;
import java.util.List;

/** Diálogo modal para crear, editar o eliminar una tarea. */
public final class TaskEditorDialog {

    /** Lo que el usuario decidió: guardar la tarea editada o eliminarla. */
    public record EditorResult(Task task, boolean delete) {
    }

    private TaskEditorDialog() {
    }

    /**
     * Carga las categorías, muestra el diálogo y guarda o elimina la tarea según lo que elija el usuario.
     * {@code refresh} se llama después (también tras mostrar un error) para recargar la pantalla.
     */
    public static void open(Window owner, AppContext context, Task task, LocalDate defaultDate,
                            Runnable refresh) {
        FxAsync.run(() -> context.categories().findAll(false), categories ->
                create(owner, context, task, defaultDate, categories).showAndWait().ifPresent(result -> {
                    if (result.delete()) {
                        if (Dialogs.confirm("¿Eliminar la tarea?",
                                "«" + result.task().getTitle() + "» se eliminará definitivamente.", "Eliminar")) {
                            FxAsync.runVoid(() -> context.tasks().delete(result.task().getId()), refresh);
                        }
                        return;
                    }
                    FxAsync.run(() -> context.tasks().save(result.task()), saved -> refresh.run(), error -> {
                        Dialogs.error(error);
                        refresh.run();
                    });
                }));
    }

    /** Crea el diálogo sin mostrarlo. */
    public static Dialog<EditorResult> create(Window owner, AppContext context, Task task, LocalDate defaultDate,
                                              List<Category> categories) {
        ViewLoader.Loaded<TaskEditorController> loaded = ViewLoader.load(context, "task-editor.fxml");
        TaskEditorController form = loaded.controller();
        form.setCategories(categories);
        form.edit(task, defaultDate);

        Dialog<EditorResult> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle(task == null ? "Nueva tarea" : "Editar tarea");
        dialog.setHeaderText(task == null ? "Nueva tarea" : "Editar tarea");
        ButtonType save = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        // Las tareas de rutina no se eliminan (se omiten desde la lista), así que no llevan este botón
        ButtonType delete = new ButtonType("Eliminar", ButtonBar.ButtonData.LEFT);
        boolean deletable = task != null && !task.isRoutineInstance();
        if (deletable) {
            dialog.getDialogPane().getButtonTypes().add(delete);
            Button deleteButton = (Button) dialog.getDialogPane().lookupButton(delete);
            deleteButton.setGraphic(new FontIcon("mdi2d-delete-outline"));
            deleteButton.getStyleClass().addAll("danger", "flat");
        }
        dialog.getDialogPane().setContent(loaded.root());
        dialog.getDialogPane().lookupButton(save).disableProperty().bind(form.invalidProperty());
        Theme.style(dialog.getDialogPane());
        dialog.setResultConverter(button -> {
            if (button == save) {
                return new EditorResult(form.applyTo(task == null ? new Task() : task), false);
            }
            if (deletable && button == delete) {
                return new EditorResult(task, true);
            }
            return null;
        });
        dialog.setOnShown(e -> Platform.runLater(form::focusTitle));
        return dialog;
    }
}
