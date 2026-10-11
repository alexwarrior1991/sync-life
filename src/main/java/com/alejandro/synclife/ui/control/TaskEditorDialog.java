package com.alejandro.synclife.ui.control;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.controller.TaskEditorController;
import com.alejandro.synclife.model.ChecklistItem;
import com.alejandro.synclife.model.entity.Category;
import com.alejandro.synclife.model.entity.Goal;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.entity.TimeEntry;
import com.alejandro.synclife.ui.Dialogs;
import com.alejandro.synclife.ui.FxAsync;
import com.alejandro.synclife.ui.Theme;
import com.alejandro.synclife.ui.ViewLoader;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ScrollPane;
import javafx.stage.Screen;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDate;
import java.util.List;

/** Diálogo modal para crear, editar o eliminar una tarea. */
public final class TaskEditorDialog {

    /** Lo que necesita el formulario además de la tarea; se carga en el hilo de base de datos. */
    public record EditorData(List<Category> categories, List<Goal> goals, List<ChecklistItem> checklist,
                             List<TimeEntry> timeEntries) {
    }

    /** Lo que el usuario decidió: guardar la tarea editada (con su lista de comprobación) o eliminarla. */
    public record EditorResult(Task task, List<ChecklistItem> checklist, boolean delete) {
    }

    /** Lo que queda de pantalla para el formulario, quitando cabecera, botones y bordes del diálogo. */
    private static final double DIALOG_CHROME_HEIGHT = 230;

    private TaskEditorDialog() {
    }

    /** Carga lo necesario para editar {@code task} (o crear una, si es null). Llamar en el hilo de BD. */
    public static EditorData load(AppContext context, Task task) {
        List<Category> categories = context.categories().findAll(false);
        List<Goal> goals = context.goals().findOpen();
        if (task == null) {
            return new EditorData(categories, goals, List.of(), List.of());
        }
        return new EditorData(categories, goals, context.tasks().checklist(task.getId()),
                context.timeTracking().entries(task.getId()));
    }

    /**
     * Carga los datos, muestra el diálogo y guarda o elimina la tarea según lo que elija el usuario.
     * {@code refresh} se llama después (también tras mostrar un error) para recargar la pantalla.
     */
    public static void open(Window owner, AppContext context, Task task, LocalDate defaultDate,
                            Runnable refresh) {
        open(owner, context, task, defaultDate, null, refresh);
    }

    /** Como {@link #open(Window, AppContext, Task, LocalDate, Runnable)}; una tarea nueva nace en {@code defaultGoal}. */
    public static void open(Window owner, AppContext context, Task task, LocalDate defaultDate, Goal defaultGoal,
                            Runnable refresh) {
        FxAsync.run(() -> load(context, task), data ->
                create(owner, context, task, defaultDate, defaultGoal, data).showAndWait().ifPresent(result -> {
                    if (result.delete()) {
                        if (Dialogs.confirm("¿Eliminar la tarea?",
                                "«" + result.task().getTitle() + "» se eliminará definitivamente.", "Eliminar")) {
                            FxAsync.runVoid(() -> context.tasks().delete(result.task().getId()), refresh);
                        }
                        return;
                    }
                    FxAsync.run(() -> context.tasks().save(result.task(), result.checklist()),
                            saved -> refresh.run(), error -> {
                                Dialogs.error(error);
                                refresh.run();
                            });
                }));
    }

    /** Crea el diálogo sin mostrarlo. */
    public static Dialog<EditorResult> create(Window owner, AppContext context, Task task, LocalDate defaultDate,
                                              EditorData data) {
        return create(owner, context, task, defaultDate, null, data);
    }

    public static Dialog<EditorResult> create(Window owner, AppContext context, Task task, LocalDate defaultDate,
                                              Goal defaultGoal, EditorData data) {
        ViewLoader.Loaded<TaskEditorController> loaded = ViewLoader.load(context, "task-editor.fxml");
        TaskEditorController form = loaded.controller();
        form.setCategories(data.categories());
        form.setGoals(data.goals(), task != null ? task.getGoal() : defaultGoal);
        form.edit(task, defaultDate, data.checklist(), data.timeEntries());
        if (task == null && defaultGoal != null) {
            form.useArea(defaultGoal.getArea());
        }

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
        dialog.getDialogPane().setContent(scrollable(loaded));
        dialog.getDialogPane().lookupButton(save).disableProperty().bind(form.invalidProperty());
        Theme.style(dialog.getDialogPane());
        dialog.setResultConverter(button -> {
            if (button == save) {
                return new EditorResult(form.applyTo(task == null ? new Task() : task), form.checklist(), false);
            }
            if (deletable && button == delete) {
                return new EditorResult(task, List.of(), true);
            }
            return null;
        });
        dialog.setOnShown(e -> Platform.runLater(form::focusTitle));
        return dialog;
    }

    /** Con una lista de comprobación larga el formulario no cabe: se desplaza en vez de salirse de la pantalla. */
    private static ScrollPane scrollable(ViewLoader.Loaded<TaskEditorController> loaded) {
        double maxHeight = Math.max(360, Screen.getPrimary().getVisualBounds().getHeight() - DIALOG_CHROME_HEIGHT);
        ScrollPane scroll = new ScrollPane(loaded.root()) {
            @Override
            protected double computePrefHeight(double width) {
                return Math.min(super.computePrefHeight(width), maxHeight);
            }
        };
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().addAll("edge-to-edge", "editor-scroll");
        return scroll;
    }
}
