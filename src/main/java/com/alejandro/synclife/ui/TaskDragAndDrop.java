package com.alejandro.synclife.ui;

import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.ui.cell.TaskListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;

import java.util.function.LongConsumer;

/**
 * Convierte una lista de tareas en destino de arrastrar y soltar. Las tareas se arrastran desde
 * {@link TaskListCell} (las de rutinas no se pueden arrastrar); soltar una llama a {@code onDrop}
 * con su id. Lo usan la Semana (cambiar de día) y la Matriz (cambiar de cuadrante).
 */
public final class TaskDragAndDrop {

    private TaskDragAndDrop() {
    }

    public static void installDropTarget(ListView<Task> list, LongConsumer onDrop) {
        list.setOnDragOver(e -> {
            if (e.getDragboard().hasContent(TaskListCell.TASK_ID) && !isFromList(e, list)) {
                e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
        });
        list.setOnDragEntered(e -> {
            if (e.getDragboard().hasContent(TaskListCell.TASK_ID) && !isFromList(e, list)) {
                list.getStyleClass().add("drag-over");
            }
        });
        list.setOnDragExited(e -> list.getStyleClass().removeAll("drag-over"));
        list.setOnDragDropped(e -> {
            Object id = e.getDragboard().getContent(TaskListCell.TASK_ID);
            boolean ok = id instanceof Long;
            if (ok) {
                onDrop.accept((Long) id);
            }
            e.setDropCompleted(ok);
            e.consume();
        });
    }

    /** Soltar en la misma lista de la que sale no cambia nada. */
    private static boolean isFromList(DragEvent e, ListView<Task> list) {
        return e.getGestureSource() instanceof TaskListCell cell && cell.getListView() == list;
    }
}
