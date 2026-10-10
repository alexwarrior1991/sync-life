package com.alejandro.synclife.ui.cell;

import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Priority;
import com.alejandro.synclife.model.enums.TaskStatus;
import com.alejandro.synclife.ui.Formats;
import com.alejandro.synclife.ui.TaskActions;
import com.alejandro.synclife.ui.Ui;
import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Celda de tarea: casilla para completar, punto de color de la categoría, título, detalles
 * (hora, duración, categoría, fecha límite), icono si viene de una rutina y menú contextual.
 * En modo compacto (vista Semana) muestra menos detalles y permite arrastrar la tarea a otro día.
 */
public class TaskListCell extends ListCell<Task> {

    /** Formato del portapapeles para arrastrar tareas entre días. */
    public static final DataFormat TASK_ID = new DataFormat("application/x-synclife-task-id");

    private static final PseudoClass DONE = PseudoClass.getPseudoClass("done");
    private static final PseudoClass CANCELLED = PseudoClass.getPseudoClass("cancelled");
    /** Ancho que ocupan en modo compacto los márgenes y la barra de color (más el icono si es de rutina). */
    private static final double COMPACT_CHROME_WIDTH = 24;
    private static final double ROUTINE_ICON_WIDTH = 18;

    private final TaskActions actions;
    private final boolean compact;

    private final CheckBox check = new CheckBox();
    private final Circle dot = new Circle(5);
    private final Label title = new Label();
    /** En modo compacto el título es un Text: con wrappingWidth calcula bien su alto en varias líneas. */
    private final Text compactTitle = new Text();
    private final Label meta = new Label();
    private final Label due = new Label();
    private final FontIcon routineIcon = new FontIcon("mdi2r-repeat");
    private final Label priority = new Label();
    private final HBox root;

    public TaskListCell(TaskActions actions, boolean compact) {
        this.actions = actions;
        this.compact = compact;
        getStyleClass().add("task-cell");
        if (compact) {
            getStyleClass().add("compact");
        }

        title.getStyleClass().add("task-title");
        title.setMinWidth(0);
        meta.getStyleClass().add("task-meta");
        due.getStyleClass().add("task-due");
        routineIcon.getStyleClass().add("routine-icon");
        Tooltip.install(routineIcon, new Tooltip("Generada por una rutina"));

        HBox details = new HBox(8, meta, due);
        details.setAlignment(Pos.CENTER_LEFT);
        details.setMinWidth(0);
        meta.setMinWidth(0);
        compactTitle.getStyleClass().add("task-title-text");
        VBox text = new VBox(2, compact ? compactTitle : title, details);
        text.setMinWidth(0);
        HBox.setHgrow(text, javafx.scene.layout.Priority.ALWAYS);

        if (compact) {
            // En la semana las columnas son estrechas: sin casilla (se completa desde el menú contextual),
            // barra de color a la izquierda y título en varias líneas
            root = new HBox(4, text, routineIcon);
            root.getStyleClass().add("compact-root");
        } else {
            root = new HBox(8, check, dot, text, routineIcon, priority);
        }
        root.setAlignment(Pos.CENTER_LEFT);
        root.setMinWidth(0);
        // Que la celda nunca pida más ancho que la lista: evita la barra de scroll horizontal
        setPrefWidth(0);

        check.setOnAction(e -> {
            Task task = getItem();
            if (task != null) {
                actions.toggleDone(task);
            }
        });
        setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && getItem() != null) {
                actions.edit(getItem());
            }
        });
        setOnDragDetected(e -> {
            Task task = getItem();
            if (task == null || task.isRoutineInstance()) {
                return;
            }
            Dragboard board = startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.put(TASK_ID, task.getId());
            board.setContent(content);
            board.setDragView(snapshot(null, null));
            e.consume();
        });
    }

    @Override
    protected void updateItem(Task task, boolean empty) {
        super.updateItem(task, empty);
        setText(null);
        if (empty || task == null) {
            setGraphic(null);
            setContextMenu(null);
            setTooltip(null);
            return;
        }
        LocalDate today = actions.today();
        if (compact && !compactTitle.wrappingWidthProperty().isBound() && getListView() != null) {
            ListView<Task> list = getListView();
            compactTitle.wrappingWidthProperty().bind(Bindings.createDoubleBinding(() -> {
                Task current = getItem();
                double chrome = COMPACT_CHROME_WIDTH
                        + (current != null && current.isRoutineInstance() ? ROUTINE_ICON_WIDTH : 0);
                return Math.max(40, list.getWidth() - chrome);
            }, list.widthProperty(), itemProperty()));
        }

        check.setSelected(task.isDone());
        dot.setFill(Color.web(Ui.colorOf(task)));
        if (compact) {
            root.setStyle("-fx-border-color: transparent transparent transparent " + Ui.colorOf(task) + ";");
        }
        title.setText(task.getTitle());
        compactTitle.setText(task.getTitle());

        List<String> parts = new ArrayList<>();
        if (task.getStartTime() != null) {
            parts.add(Formats.time(task.getStartTime()));
        }
        if (task.getEstimatedMinutes() != null && task.getEstimatedMinutes() > 0) {
            parts.add(Formats.duration(task.getEstimatedMinutes()));
        }
        if (!compact) {
            parts.add(task.getCategory() != null ? task.getCategory().getName() : task.getArea().getDisplayName());
            if (task.getScheduledDate() != null && !task.getScheduledDate().equals(today) && task.isOpen()) {
                parts.add(Formats.relativeDay(task.getScheduledDate(), today));
            }
        }
        meta.setText(String.join(" · ", parts));
        meta.setVisible(!parts.isEmpty());
        meta.setManaged(!parts.isEmpty());

        boolean showDue = task.getDueDate() != null && task.isOpen() && !compact;
        due.setVisible(showDue);
        due.setManaged(showDue);
        due.getStyleClass().remove("overdue");
        if (showDue) {
            if (task.isOverdue(today)) {
                due.setText("Venció " + Formats.relativeDay(task.getDueDate(), today).toLowerCase());
                due.getStyleClass().add("overdue");
            } else {
                due.setText("Vence " + Formats.relativeDay(task.getDueDate(), today).toLowerCase());
            }
        }

        routineIcon.setVisible(task.isRoutineInstance());
        routineIcon.setManaged(task.isRoutineInstance());

        boolean important = task.getPriority() == Priority.ALTA || task.getPriority() == Priority.URGENTE;
        priority.setMinWidth(Region.USE_PREF_SIZE);
        priority.setVisible(important && task.isOpen());
        priority.setManaged(important && task.isOpen());
        priority.setText(task.getPriority().getDisplayName());
        priority.getStyleClass().setAll("badge", "priority-" + task.getPriority().name().toLowerCase());

        root.pseudoClassStateChanged(DONE, task.isDone());
        root.pseudoClassStateChanged(CANCELLED, task.getStatus() == TaskStatus.CANCELADA);

        setTooltip(task.getDescription() == null ? null : new Tooltip(task.getDescription()));
        setContextMenu(buildMenu(task, today));
        setGraphic(root);
    }

    private ContextMenu buildMenu(Task task, LocalDate today) {
        ContextMenu menu = new ContextMenu();
        MenuItem edit = new MenuItem("Editar…", new FontIcon("mdi2p-pencil-outline"));
        edit.setOnAction(e -> actions.edit(task));
        MenuItem toggle = new MenuItem(task.isOpen() ? "Marcar como hecha" : "Reabrir",
                new FontIcon(task.isOpen() ? "mdi2c-check" : "mdi2r-refresh"));
        toggle.setOnAction(e -> actions.toggleDone(task));
        menu.getItems().addAll(edit, toggle);

        if (task.isRoutineInstance()) {
            if (task.isOpen()) {
                MenuItem skip = new MenuItem("Omitir este día", new FontIcon("mdi2s-skip-next"));
                skip.setOnAction(e -> actions.skip(task));
                menu.getItems().add(skip);
            }
            return menu;
        }

        if (task.isOpen()) {
            menu.getItems().add(new SeparatorMenuItem());
            if (!today.equals(task.getScheduledDate())) {
                MenuItem toToday = new MenuItem("Mover a hoy", new FontIcon("mdi2c-calendar-today"));
                toToday.setOnAction(e -> actions.moveTo(task, today));
                menu.getItems().add(toToday);
            }
            LocalDate tomorrow = (task.getScheduledDate() != null && task.getScheduledDate().isAfter(today)
                    ? task.getScheduledDate() : today).plusDays(1);
            String laterText = tomorrow.equals(today.plusDays(1))
                    ? "Mover a mañana"
                    : "Mover al " + Formats.relativeDay(tomorrow, today).toLowerCase();
            MenuItem later = new MenuItem(laterText, new FontIcon("mdi2c-calendar-arrow-right"));
            later.setOnAction(e -> actions.moveTo(task, tomorrow));
            menu.getItems().add(later);
            if (task.getScheduledDate() != null) {
                MenuItem unschedule = new MenuItem("Quitar fecha", new FontIcon("mdi2c-calendar-blank-outline"));
                unschedule.setOnAction(e -> actions.moveTo(task, null));
                menu.getItems().add(unschedule);
            }
        }
        MenuItem delete = new MenuItem("Eliminar", new FontIcon("mdi2d-delete-outline"));
        delete.setOnAction(e -> actions.delete(task));
        menu.getItems().addAll(new SeparatorMenuItem(), delete);
        return menu;
    }
}
