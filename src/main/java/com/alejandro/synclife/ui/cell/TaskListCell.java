package com.alejandro.synclife.ui.cell;

import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Priority;
import com.alejandro.synclife.model.enums.Quadrant;
import com.alejandro.synclife.model.enums.TaskStatus;
import com.alejandro.synclife.ui.Formats;
import com.alejandro.synclife.ui.TaskActions;
import com.alejandro.synclife.ui.Ui;
import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
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
    /** "2/5" de la lista de comprobación y "50 min / 1 h" de tiempo registrado. */
    private final Label checklist = new Label(null, new FontIcon("mdi2c-checkbox-marked-outline"));
    private final Label tracked = new Label(null, new FontIcon("mdi2c-clock-outline"));
    /** Objetivo al que contribuye la tarea. */
    private final Label goal = new Label(null, new FontIcon("mdi2b-bullseye-arrow"));
    private final Tooltip goalTip = new Tooltip();
    private final FontIcon pomodoroIcon = new FontIcon("mdi2t-timer-outline");
    private final FontIcon routineIcon = new FontIcon("mdi2r-repeat");
    private final Label priority = new Label();
    /** Papelera (o "omitir" si es de rutina), siempre a mano al final de la fila. */
    private final FontIcon rowActionIcon = new FontIcon("mdi2d-delete-outline");
    private final Button rowAction = new Button(null, rowActionIcon);
    private final Tooltip rowActionTip = new Tooltip();
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
        pomodoroIcon.getStyleClass().add("pomodoro-active-icon");
        Tooltip.install(pomodoroIcon, new Tooltip("Pomodoro en marcha"));
        checklist.getStyleClass().add("task-indicator");
        tracked.getStyleClass().add("task-indicator");
        checklist.setMinWidth(Region.USE_PREF_SIZE);
        tracked.setMinWidth(Region.USE_PREF_SIZE);
        Tooltip.install(checklist, new Tooltip("Pasos hechos de la lista de comprobación"));
        Tooltip.install(tracked, new Tooltip("Tiempo real registrado / estimado"));

        goal.getStyleClass().addAll("task-indicator", "goal-indicator");
        goal.setMinWidth(0);
        goal.setMaxWidth(170);
        goal.setTooltip(goalTip);

        HBox details = compact ? new HBox(8, meta, checklist) : new HBox(8, meta, checklist, tracked, goal, due);
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
            root = new HBox(4, text, pomodoroIcon, routineIcon);
            root.getStyleClass().add("compact-root");
        } else {
            rowAction.getStyleClass().addAll("button-icon", "flat", "small", "row-action");
            rowAction.setTooltip(rowActionTip);
            rowAction.setFocusTraversable(false);
            rowAction.setOnAction(e -> {
                Task task = getItem();
                if (task != null) {
                    actions.deleteOrSkip(task);
                }
            });
            root = new HBox(8, check, dot, text, pomodoroIcon, routineIcon, priority, rowAction);
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
                        + (current != null && current.isRoutineInstance() ? ROUTINE_ICON_WIDTH : 0)
                        + (current != null && actions.isPomodoroTask(current) ? ROUTINE_ICON_WIDTH : 0);
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

        boolean hasChecklist = task.getSubtaskTotal() > 0;
        checklist.setText(task.getSubtaskDone() + "/" + task.getSubtaskTotal());
        checklist.setVisible(hasChecklist);
        checklist.setManaged(hasChecklist);
        checklist.getStyleClass().remove("complete");
        if (hasChecklist && task.getSubtaskDone() == task.getSubtaskTotal()) {
            checklist.getStyleClass().add("complete");
        }

        boolean hasTime = task.getTrackedMinutes() > 0;
        Integer estimate = task.getEstimatedMinutes();
        tracked.setText(estimate == null || estimate == 0
                ? Formats.duration(task.getTrackedMinutes())
                : Formats.duration(task.getTrackedMinutes()) + " / " + Formats.duration(estimate));
        tracked.setVisible(hasTime);
        tracked.setManaged(hasTime);
        tracked.getStyleClass().remove("over");
        if (hasTime && estimate != null && estimate > 0 && task.getTrackedMinutes() > estimate * 1.1) {
            tracked.getStyleClass().add("over");
        }

        boolean hasGoal = task.getGoal() != null && !compact && actions.showsGoal();
        goal.setVisible(hasGoal);
        goal.setManaged(hasGoal);
        if (hasGoal) {
            goal.setText(task.getGoal().getTitle());
            goalTip.setText("Objetivo: " + task.getGoal().getTitle());
        }

        boolean pomodoro = actions.isPomodoroTask(task);
        pomodoroIcon.setVisible(pomodoro);
        pomodoroIcon.setManaged(pomodoro);

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

        if (!compact) {
            updateRowAction(task);
        }

        root.pseudoClassStateChanged(DONE, task.isDone());
        root.pseudoClassStateChanged(CANCELLED, task.getStatus() == TaskStatus.CANCELADA);

        setTooltip(task.getDescription() == null ? null : new Tooltip(task.getDescription()));
        setContextMenu(buildMenu(task, today));
        setGraphic(root);
    }

    private void updateRowAction(Task task) {
        boolean routine = task.isRoutineInstance();
        // Una tarea de rutina ya hecha u omitida no tiene acción: no se borra ni se vuelve a omitir
        boolean available = !routine || task.isOpen();
        rowAction.setVisible(available);
        rowAction.setManaged(available);
        rowActionIcon.setIconLiteral(routine ? "mdi2s-skip-next" : "mdi2d-delete-outline");
        rowActionTip.setText(routine ? "Omitir este día (Supr)" : "Eliminar tarea (Supr)");
        rowAction.getStyleClass().remove("danger");
        if (!routine) {
            rowAction.getStyleClass().add("danger");
        }
    }

    private ContextMenu buildMenu(Task task, LocalDate today) {
        ContextMenu menu = new ContextMenu();
        MenuItem edit = new MenuItem("Editar…", new FontIcon("mdi2p-pencil-outline"));
        edit.setOnAction(e -> actions.edit(task));
        MenuItem toggle = new MenuItem(task.isOpen() ? "Marcar como hecha" : "Reabrir",
                new FontIcon(task.isOpen() ? "mdi2c-check" : "mdi2r-refresh"));
        toggle.setOnAction(e -> actions.toggleDone(task));
        menu.getItems().addAll(edit, toggle);
        if (task.isOpen()) {
            MenuItem pomodoro = new MenuItem("Iniciar pomodoro", new FontIcon("mdi2t-timer-outline"));
            pomodoro.setOnAction(e -> actions.startPomodoro(task));
            pomodoro.setDisable(actions.isPomodoroTask(task));
            menu.getItems().add(pomodoro);
        }

        if (task.isRoutineInstance()) {
            if (task.isOpen()) {
                MenuItem skip = new MenuItem("Omitir este día", new FontIcon("mdi2s-skip-next"));
                skip.setOnAction(e -> actions.skip(task));
                menu.getItems().add(skip);
            }
            return menu;
        }

        if (task.getGoal() != null) {
            MenuItem unlink = new MenuItem("Quitar del objetivo «" + task.getGoal().getTitle() + "»",
                    new FontIcon("mdi2l-link-variant-off"));
            unlink.setOnAction(e -> actions.unlinkGoal(task));
            menu.getItems().add(unlink);
        }
        if (task.isOpen()) {
            menu.getItems().add(quadrantMenu(task, actions));
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

    /** Submenú "Matriz" para clasificar la tarea sin abrir el editor (también lo usa la tabla de Tareas). */
    public static Menu quadrantMenu(Task task, TaskActions actions) {
        Menu menu = new Menu("Matriz", new FontIcon("mdi2v-view-grid-outline"));
        ToggleGroup group = new ToggleGroup();
        for (Quadrant quadrant : Quadrant.values()) {
            menu.getItems().add(quadrantItem(task, actions, group, quadrant,
                    quadrant.getDisplayName() + " · " + quadrant.getDescription().toLowerCase()));
        }
        menu.getItems().add(new SeparatorMenuItem());
        menu.getItems().add(quadrantItem(task, actions, group, null, "Sin clasificar"));
        return menu;
    }

    private static RadioMenuItem quadrantItem(Task task, TaskActions actions, ToggleGroup group, Quadrant quadrant,
                                              String text) {
        RadioMenuItem item = new RadioMenuItem(text);
        item.setToggleGroup(group);
        item.setSelected(task.getQuadrant() == quadrant);
        item.setOnAction(e -> {
            if (task.getQuadrant() != quadrant) {
                actions.setQuadrant(task, quadrant);
            }
        });
        return item;
    }
}
