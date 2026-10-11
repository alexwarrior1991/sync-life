package com.alejandro.synclife.controller;

import atlantafx.base.controls.CustomTextField;
import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.model.TaskFilter;
import com.alejandro.synclife.model.entity.Category;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.Priority;
import com.alejandro.synclife.model.enums.TaskStatus;
import com.alejandro.synclife.ui.Choice;
import com.alejandro.synclife.ui.Formats;
import com.alejandro.synclife.ui.FxAsync;
import com.alejandro.synclife.ui.TaskActions;
import com.alejandro.synclife.ui.Ui;
import com.alejandro.synclife.ui.ViewController;
import com.alejandro.synclife.ui.cell.CategoryChoiceCell;
import com.alejandro.synclife.ui.cell.TaskListCell;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Pantalla "Tareas": todas las tareas en una tabla con filtros combinables y ordenación. */
public class TasksController implements ViewController {

    private enum DateRange {
        TODAS("Cualquier fecha"),
        HOY("Hoy"),
        SEMANA("Esta semana"),
        PROXIMOS_30("Próximos 30 días"),
        VENCIDAS("Vencidas"),
        SIN_PROGRAMAR("Sin programar");

        private final String label;

        DateRange(String label) {
            this.label = label;
        }
    }

    private static final PseudoClass DONE = PseudoClass.getPseudoClass("done");

    private final AppContext context;
    private final ObservableList<Task> items = FXCollections.observableArrayList();
    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(250));
    private TaskActions actions;
    private List<Category> categories = List.of();
    private boolean resetting;

    @FXML private Label countLabel;
    @FXML private Button deleteDoneButton;
    @FXML private Button deleteSelectedButton;
    @FXML private CustomTextField searchField;
    @FXML private ComboBox<Choice<Area>> areaFilter;
    @FXML private ComboBox<Choice<Category>> categoryFilter;
    @FXML private ComboBox<Choice<Set<TaskStatus>>> statusFilter;
    @FXML private ComboBox<Choice<Priority>> priorityFilter;
    @FXML private ComboBox<Choice<DateRange>> dateFilter;
    @FXML private CheckBox includeRoutines;
    @FXML private TableView<Task> table;

    public TasksController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        actions = new TaskActions(context, table, this::onShow);

        areaFilter.getItems().add(Choice.of("Todas las áreas", null));
        for (Area area : Area.values()) {
            areaFilter.getItems().add(Choice.of(area.getDisplayName(), area));
        }
        statusFilter.getItems().addAll(
                Choice.of("Abiertas", TaskStatus.OPEN),
                Choice.of("Completadas", EnumSet.of(TaskStatus.COMPLETADA)),
                Choice.of("Canceladas", EnumSet.of(TaskStatus.CANCELADA)),
                Choice.of("Todos los estados", null));
        priorityFilter.getItems().add(Choice.of("Cualquier prioridad", null));
        for (Priority priority : List.of(Priority.URGENTE, Priority.ALTA, Priority.MEDIA, Priority.BAJA)) {
            priorityFilter.getItems().add(Choice.of(priority.getDisplayName(), priority));
        }
        for (DateRange range : DateRange.values()) {
            dateFilter.getItems().add(Choice.of(range.label, range));
        }
        categoryFilter.setCellFactory(list -> new CategoryChoiceCell());
        categoryFilter.setButtonCell(new CategoryChoiceCell());
        resetFilters();

        areaFilter.valueProperty().addListener((obs, old, value) -> {
            refreshCategoryFilter();
            reload();
        });
        for (ComboBox<?> combo : List.of(categoryFilter, statusFilter, priorityFilter, dateFilter)) {
            combo.valueProperty().addListener((obs, old, value) -> reload());
        }
        includeRoutines.selectedProperty().addListener((obs, old, value) -> reload());
        searchDelay.setOnFinished(e -> reload());
        searchField.textProperty().addListener((obs, old, value) -> searchDelay.playFromStart());

        buildTable();
        updateDeleteButtons();
    }

    private void buildTable() {
        TableColumn<Task, Task> done = new TableColumn<>("");
        done.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        done.setCellFactory(col -> new TableCell<>() {
            private final CheckBox check = new CheckBox();

            {
                check.setOnAction(e -> {
                    if (getItem() != null) {
                        actions.toggleDone(getItem());
                    }
                });
            }

            @Override
            protected void updateItem(Task task, boolean empty) {
                super.updateItem(task, empty);
                if (empty || task == null) {
                    setGraphic(null);
                } else {
                    check.setSelected(task.isDone());
                    setGraphic(check);
                }
            }
        });
        done.setSortable(false);
        done.setPrefWidth(40);
        done.setMinWidth(40);
        done.setMaxWidth(40);

        TableColumn<Task, Task> title = new TableColumn<>("Título");
        title.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        title.setComparator(Comparator.comparing(Task::getTitle, String.CASE_INSENSITIVE_ORDER));
        title.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Task task, boolean empty) {
                super.updateItem(task, empty);
                if (empty || task == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(task.getTitle());
                setGraphic(task.isRoutineInstance() ? new FontIcon("mdi2r-repeat") : null);
                setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
            }
        });
        title.setPrefWidth(270);
        title.setMinWidth(140);

        TableColumn<Task, Area> area = graphicColumn("Área", Task::getArea, Ui::areaChip);
        area.setPrefWidth(120);
        area.setMinWidth(110);

        TableColumn<Task, Category> category = new TableColumn<>("Categoría");
        category.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getCategory()));
        category.setComparator(Comparator.nullsLast(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER)));
        category.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Category value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : value.getName());
                setGraphic(empty || value == null ? null : Ui.dot(value.getColorHex(), 4));
            }
        });
        category.setPrefWidth(160);
        category.setMinWidth(100);

        TableColumn<Task, Priority> priority = graphicColumn("Prioridad", Task::getPriority, Ui::priorityBadge);
        priority.setPrefWidth(105);
        priority.setMinWidth(100);

        TableColumn<Task, TaskStatus> status = textColumn("Estado", Task::getStatus, TaskStatus::getDisplayName);
        status.setPrefWidth(120);
        status.setMinWidth(105);

        // Día y hora en una sola columna: "Hoy 10:00"
        TableColumn<Task, Task> scheduled = new TableColumn<>("Programada");
        scheduled.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        scheduled.setComparator(Comparator.comparing(Task::getScheduledDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Task::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())));
        scheduled.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Task task, boolean empty) {
                super.updateItem(task, empty);
                if (empty || task == null || task.getScheduledDate() == null) {
                    setText(null);
                    return;
                }
                String day = Formats.relativeDay(task.getScheduledDate(), context.today());
                setText(task.getStartTime() == null ? day : day + " " + Formats.time(task.getStartTime()));
            }
        });
        scheduled.setPrefWidth(150);
        scheduled.setMinWidth(135);

        TableColumn<Task, Task> due = new TableColumn<>("Vence");
        due.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        due.setComparator(Comparator.comparing(Task::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())));
        due.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Task task, boolean empty) {
                super.updateItem(task, empty);
                getStyleClass().remove("overdue");
                if (empty || task == null || task.getDueDate() == null) {
                    setText(null);
                    return;
                }
                setText(Formats.relativeDay(task.getDueDate(), context.today()));
                if (task.isOverdue(context.today())) {
                    getStyleClass().add("overdue");
                }
            }
        });
        due.setPrefWidth(115);
        due.setMinWidth(100);

        // Estimación y, si hay tiempo registrado, "real / estimado" con un reloj (como en las listas);
        // en rojo si lo real supera lo estimado en más de un 10 %
        TableColumn<Task, Task> time = new TableColumn<>("Tiempo");
        time.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        time.setComparator(Comparator.comparing(Task::getEstimatedMinutes, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(Task::getTrackedMinutes));
        time.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Task task, boolean empty) {
                super.updateItem(task, empty);
                getStyleClass().remove("over");
                setGraphic(null);
                setTooltip(null);
                if (empty || task == null) {
                    setText(null);
                    return;
                }
                Integer estimated = task.getEstimatedMinutes();
                int tracked = task.getTrackedMinutes();
                if (tracked == 0) {
                    setText(Formats.duration(estimated));
                    return;
                }
                setGraphic(new FontIcon("mdi2c-clock-outline"));
                setText(estimated == null || estimated == 0 ? Formats.duration(tracked)
                        : Formats.duration(tracked) + " / " + Formats.duration(estimated));
                setTooltip(new javafx.scene.control.Tooltip("Real registrado: "
                        + Formats.trackedVsEstimate(tracked, estimated)));
                if (estimated != null && estimated > 0 && tracked > estimated * 1.1) {
                    getStyleClass().add("over");
                }
            }
        });
        time.setPrefWidth(130);
        time.setMinWidth(125);

        table.getColumns().setAll(List.of(done, title, area, category, priority, status, scheduled, due, time));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        SortedList<Task> sorted = new SortedList<>(items);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        Label empty = new Label("No hay tareas que coincidan con los filtros");
        empty.getStyleClass().add("placeholder");
        table.setPlaceholder(empty);

        // Selección múltiple (Ctrl/Mayús + clic) para borrar varias a la vez; Supr borra la selección
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.getSelectionModel().getSelectedItems().addListener(
                (ListChangeListener<Task>) change -> updateDeleteButtons());
        table.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.DELETE && !table.getSelectionModel().isEmpty()) {
                deleteSelected();
                e.consume();
            }
        });

        table.setRowFactory(tv -> {
            ContextMenu menu = new ContextMenu();
            TableRow<Task> row = new TableRow<>() {
                @Override
                protected void updateItem(Task task, boolean empty) {
                    super.updateItem(task, empty);
                    pseudoClassStateChanged(DONE, !empty && task != null && !task.isOpen());
                    setContextMenu(empty || task == null ? null : menu);
                }
            };
            // El menú se construye al abrirlo: depende de cuántas filas haya seleccionadas en ese momento
            menu.setOnShowing(e -> {
                if (row.getItem() != null) {
                    menu.getItems().setAll(rowMenuItems(row.getItem()));
                }
            });
            row.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !row.isEmpty()) {
                    actions.edit(row.getItem());
                }
            });
            return row;
        });
    }

    private List<MenuItem> rowMenuItems(Task task) {
        List<Task> selected = table.getSelectionModel().getSelectedItems();
        if (selected.size() > 1 && selected.contains(task)) {
            MenuItem deleteSelected = new MenuItem("Eliminar seleccionadas (" + selected.size() + ")",
                    new FontIcon("mdi2d-delete-sweep-outline"));
            deleteSelected.setOnAction(e -> deleteSelected());
            return List.of(deleteSelected);
        }
        MenuItem edit = new MenuItem("Editar…", new FontIcon("mdi2p-pencil-outline"));
        edit.setOnAction(e -> actions.edit(task));
        MenuItem toggle = new MenuItem(task.isOpen() ? "Marcar como hecha" : "Reabrir",
                new FontIcon(task.isOpen() ? "mdi2c-check" : "mdi2r-refresh"));
        toggle.setOnAction(e -> actions.toggleDone(task));
        List<MenuItem> items = new ArrayList<>(List.of(edit, toggle));
        if (task.isOpen()) {
            MenuItem pomodoro = new MenuItem("Iniciar pomodoro", new FontIcon("mdi2t-timer-outline"));
            pomodoro.setOnAction(e -> actions.startPomodoro(task));
            pomodoro.setDisable(actions.isPomodoroTask(task));
            items.add(pomodoro);
            if (!task.isRoutineInstance()) {
                items.add(TaskListCell.quadrantMenu(task, actions));
            }
        }
        if (task.isRoutineInstance()) {
            if (task.isOpen()) {
                MenuItem skip = new MenuItem("Omitir este día", new FontIcon("mdi2s-skip-next"));
                skip.setOnAction(e -> actions.skip(task));
                items.add(skip);
            }
        } else {
            MenuItem toToday = new MenuItem("Mover a hoy", new FontIcon("mdi2c-calendar-today"));
            toToday.setOnAction(e -> actions.moveTo(task, context.today()));
            MenuItem delete = new MenuItem("Eliminar", new FontIcon("mdi2d-delete-outline"));
            delete.setOnAction(e -> actions.delete(task));
            items.addAll(List.of(toToday, new SeparatorMenuItem(), delete));
        }
        return items;
    }

    /** Actualiza el texto y el estado de "Eliminar seleccionadas (N)" y "Borrar completadas (N)". */
    private void updateDeleteButtons() {
        int selected = table.getSelectionModel().getSelectedItems().size();
        deleteSelectedButton.setDisable(selected == 0);
        deleteSelectedButton.setText(selected == 0 ? "Eliminar seleccionadas" : "Eliminar seleccionadas (" + selected + ")");
        int completed = completedVisible().size();
        deleteDoneButton.setDisable(completed == 0);
        deleteDoneButton.setText(completed == 0 ? "Borrar completadas" : "Borrar completadas (" + completed + ")");
    }

    /** Tareas completadas (no de rutina) de la lista visible: respeta los filtros actuales. */
    private List<Task> completedVisible() {
        return items.stream().filter(t -> t.isDone() && !t.isRoutineInstance()).toList();
    }

    @FXML
    private void deleteSelected() {
        actions.deleteMany(new ArrayList<>(table.getSelectionModel().getSelectedItems()));
    }

    @FXML
    private void deleteCompleted() {
        actions.deleteMany(completedVisible());
    }

    private static <V> TableColumn<Task, V> textColumn(String name, Function<Task, V> getter, Function<V, String> format) {
        TableColumn<Task, V> column = new TableColumn<>(name);
        column.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(getter.apply(cd.getValue())));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(V value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : format.apply(value));
            }
        });
        return column;
    }

    private static <V> TableColumn<Task, V> graphicColumn(String name, Function<Task, V> getter,
                                                          Function<V, javafx.scene.Node> graphic) {
        TableColumn<Task, V> column = new TableColumn<>(name);
        column.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(getter.apply(cd.getValue())));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(V value, boolean empty) {
                super.updateItem(value, empty);
                setText(null);
                if (empty || value == null) {
                    setGraphic(null);
                } else {
                    HBox box = new HBox(graphic.apply(value));
                    box.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(box);
                }
            }
        });
        return column;
    }

    // ---------------------------------------------------------------- datos

    @Override
    public void onShow() {
        FxAsync.run(() -> context.categories().findAll(true), loaded -> {
            categories = loaded;
            refreshCategoryFilter();
            reload();
        });
    }

    private void reload() {
        if (resetting) {
            return;
        }
        TaskFilter filter = buildFilter();
        FxAsync.run(() -> context.tasks().search(filter), tasks -> {
            items.setAll(tasks);
            updateDeleteButtons();
            long open = tasks.stream().filter(Task::isOpen).count();
            countLabel.setText(tasks.size() + (tasks.size() == 1 ? " tarea" : " tareas")
                    + " · " + open + (open == 1 ? " abierta" : " abiertas"));
        });
    }

    private TaskFilter buildFilter() {
        LocalDate today = context.today();
        TaskFilter filter = TaskFilter.all()
                .text(searchField.getText())
                .area(Ui.choiceValue(areaFilter))
                .priority(Ui.choiceValue(priorityFilter))
                .includeRoutineInstances(includeRoutines.isSelected());
        Category category = Ui.choiceValue(categoryFilter);
        if (category != null) {
            filter.categoryId(category.getId());
        }
        Set<TaskStatus> statuses = Ui.choiceValue(statusFilter);
        filter.statuses(statuses);
        DateRange range = Ui.choiceValue(dateFilter);
        if (range != null) {
            switch (range) {
                case HOY -> filter.scheduledBetween(today, today);
                case SEMANA -> filter.scheduledBetween(Formats.mondayOf(today), Formats.mondayOf(today).plusDays(6));
                case PROXIMOS_30 -> filter.scheduledBetween(today, today.plusDays(30));
                case VENCIDAS -> filter.dueBefore(today).statuses(TaskStatus.OPEN);
                case SIN_PROGRAMAR -> filter.unscheduledOnly(true);
                case TODAS -> { }
            }
        }
        return filter;
    }

    private void refreshCategoryFilter() {
        Category selected = Ui.choiceValue(categoryFilter);
        Area area = Ui.choiceValue(areaFilter);
        boolean wasResetting = resetting;
        resetting = true;
        try {
            categoryFilter.getItems().setAll(Choice.of("Todas las categorías", null));
            for (Category category : categories) {
                if (area == null || category.getArea() == area) {
                    categoryFilter.getItems().add(Choice.of(category.getName(), category));
                }
            }
            Ui.selectChoice(categoryFilter, selected != null && (area == null || selected.getArea() == area)
                    ? selected : null);
        } finally {
            resetting = wasResetting;
        }
    }

    private void resetFilters() {
        resetting = true;
        try {
            searchField.clear();
            areaFilter.getSelectionModel().selectFirst();
            refreshCategoryFilter();
            statusFilter.getSelectionModel().selectFirst();
            priorityFilter.getSelectionModel().selectFirst();
            dateFilter.getSelectionModel().selectFirst();
            includeRoutines.setSelected(false);
        } finally {
            resetting = false;
        }
    }

    @FXML
    private void clearFilters() {
        resetFilters();
        searchDelay.stop();
        reload();
    }

    @FXML
    private void newTask() {
        actions.create(context.today());
    }
}
