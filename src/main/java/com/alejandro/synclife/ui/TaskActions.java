package com.alejandro.synclife.ui;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Quadrant;
import com.alejandro.synclife.ui.control.TaskEditorDialog;
import javafx.scene.Node;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Window;

import java.time.LocalDate;
import java.util.List;
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
        if (task.isOpen() && isPomodoroTask(task)) {
            // Completar la tarea del Pomodoro lo termina y registra el tiempo que lleva
            AppUi.pomodoro().stop();
            AppUi.notifications().success("Pomodoro terminado: «" + task.getTitle() + "» está hecha.");
        }
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

    /** Empieza un Pomodoro con la tarea (la tarjeta de la barra lateral muestra la cuenta atrás). */
    public void startPomodoro(Task task) {
        AppUi.pomodoro().start(task);
    }

    /** ¿Hay un Pomodoro en marcha con esta tarea? (para destacarla en las listas) */
    public boolean isPomodoroTask(Task task) {
        return AppUi.isInstalled() && AppUi.pomodoro().isWorkingOn(task);
    }

    /** Clasifica la tarea en la matriz de Eisenhower ({@code null} = sin clasificar). */
    public void setQuadrant(Task task, Quadrant quadrant) {
        setQuadrant(task.getId(), quadrant);
    }

    public void setQuadrant(long taskId, Quadrant quadrant) {
        execute(() -> context.tasks().setQuadrant(taskId, quadrant));
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

    /** Lo que hacen la papelera de cada fila y la tecla Supr: borrar o, si es de una rutina, omitir. */
    public void deleteOrSkip(Task task) {
        if (!task.isRoutineInstance()) {
            delete(task);
        } else if (task.isOpen()) {
            skip(task);
        }
    }

    /** Borra varias tareas tras confirmar. Las de rutinas se mantienen (se omiten, no se borran). */
    public void deleteMany(List<Task> tasks) {
        if (tasks.isEmpty()) {
            return;
        }
        long routine = tasks.stream().filter(Task::isRoutineInstance).count();
        long ordinary = tasks.size() - routine;
        if (ordinary == 0) {
            Dialogs.info("No hay nada que eliminar",
                    "Las tareas generadas por una rutina no se eliminan: usa «Omitir» o edita la rutina.");
            return;
        }
        String header = ordinary == 1 ? "¿Eliminar 1 tarea?" : "¿Eliminar " + ordinary + " tareas?";
        String detail = ordinary == 1 ? "Se eliminará definitivamente." : "Se eliminarán definitivamente.";
        if (routine > 0) {
            detail += "\n" + routine + (routine == 1 ? " tarea de rutina se mantiene" : " tareas de rutina se mantienen")
                    + " (para ellas usa «Omitir»).";
        }
        if (Dialogs.confirm(header, detail, "Eliminar")) {
            List<Long> ids = tasks.stream().map(Task::getId).toList();
            execute(() -> context.tasks().deleteAll(ids));
        }
    }

    /** La tecla Supr borra (u omite, si es de rutina) la tarea seleccionada en la lista. */
    public void installDeleteKey(ListView<Task> list) {
        list.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            Task selected = list.getSelectionModel().getSelectedItem();
            if (e.getCode() == KeyCode.DELETE && selected != null) {
                deleteOrSkip(selected);
                e.consume();
            }
        });
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
