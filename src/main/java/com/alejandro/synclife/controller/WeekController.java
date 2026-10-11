package com.alejandro.synclife.controller;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.ui.AppUi;
import com.alejandro.synclife.ui.Formats;
import com.alejandro.synclife.ui.FxAsync;
import com.alejandro.synclife.ui.TaskActions;
import com.alejandro.synclife.ui.TaskDragAndDrop;
import com.alejandro.synclife.ui.Ui;
import com.alejandro.synclife.ui.View;
import com.alejandro.synclife.ui.ViewController;
import com.alejandro.synclife.ui.cell.TaskListCell;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Pantalla "Semana": siete columnas (L-D) con las tareas de cada día y arrastrar y soltar para reprogramar. */
public class WeekController implements ViewController {

    private record WeekData(Map<LocalDate, List<Task>> week, List<Task> unscheduled, List<Task> priorities) {
    }

    /** Columna de un día. La fecha cambia al navegar entre semanas. */
    private static final class DayColumn {
        private LocalDate date;
        private final VBox root;
        private final Label name;
        private final Label total;
        private final ListView<Task> list;

        private DayColumn(VBox root, Label name, Label total, ListView<Task> list) {
            this.root = root;
            this.name = name;
            this.total = total;
            this.list = list;
        }
    }

    private static final PseudoClass DONE = PseudoClass.getPseudoClass("done");

    private final AppContext context;
    private final List<DayColumn> columns = new ArrayList<>(7);
    private final ToggleGroup areaGroup = new ToggleGroup();
    private TaskActions actions;
    private LocalDate monday;
    private WeekData data;

    @FXML private Label weekTitle;
    @FXML private Label weekSubtitle;
    @FXML private HBox areaFilterBox;
    @FXML private HBox prioritiesBox;
    @FXML private GridPane weekGrid;
    @FXML private ToggleButton unscheduledToggle;
    @FXML private VBox unscheduledBox;
    @FXML private ListView<Task> unscheduledList;

    public WeekController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        monday = Formats.mondayOf(context.today());
        actions = new TaskActions(context, weekGrid, this::onShow);
        buildAreaFilter();
        buildColumns();

        unscheduledList.setCellFactory(list -> new TaskListCell(actions, true));
        actions.installDeleteKey(unscheduledList);
        Label empty = new Label("Nada pendiente sin fecha");
        empty.getStyleClass().add("placeholder");
        unscheduledList.setPlaceholder(empty);
        installDropTarget(unscheduledList, () -> null);
        unscheduledBox.visibleProperty().bind(unscheduledToggle.selectedProperty());
        unscheduledBox.managedProperty().bind(unscheduledToggle.selectedProperty());
    }

    private void buildAreaFilter() {
        String[] pills = {"left-pill", "center-pill", "center-pill", "right-pill"};
        List<Area> options = new ArrayList<>();
        options.add(null);
        options.addAll(List.of(Area.values()));
        for (int i = 0; i < options.size(); i++) {
            Area area = options.get(i);
            ToggleButton button = new ToggleButton(area == null ? "Todas" : area.getDisplayName());
            button.getStyleClass().add(pills[i]);
            button.setUserData(area);
            button.setToggleGroup(areaGroup);
            areaFilterBox.getChildren().add(button);
            if (area == null) {
                button.setSelected(true);
            }
        }
        areaGroup.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                areaGroup.selectToggle(old);
            } else if (data != null) {
                apply(data);
            }
        });
    }

    private void buildColumns() {
        RowConstraints row = new RowConstraints();
        row.setVgrow(Priority.ALWAYS);
        row.setFillHeight(true);
        weekGrid.getRowConstraints().add(row);
        for (int i = 0; i < 7; i++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(100.0 / 7);
            constraints.setHgrow(Priority.ALWAYS);
            weekGrid.getColumnConstraints().add(constraints);

            Label name = new Label();
            name.getStyleClass().add("day-name");
            name.setMinWidth(Region.USE_PREF_SIZE);
            Label total = new Label();
            total.getStyleClass().addAll("text-muted", "text-small");
            VBox titles = new VBox(0, name, total);
            titles.setMinWidth(0);
            Button add = new Button(null, new FontIcon("mdi2p-plus"));
            add.getStyleClass().addAll("button-icon", "flat", "small");
            add.setMinWidth(Region.USE_PREF_SIZE);
            add.setTooltip(new Tooltip("Nueva tarea este día"));
            HBox header = new HBox(2, titles, Ui.hSpacer(), add);
            header.setAlignment(Pos.CENTER_LEFT);
            header.getStyleClass().add("day-header");

            ListView<Task> list = new ListView<>();
            list.getStyleClass().add("day-list");
            list.setCellFactory(l -> new TaskListCell(actions, true));
            actions.installDeleteKey(list);
            VBox.setVgrow(list, Priority.ALWAYS);

            VBox root = new VBox(4, header, list);
            root.getStyleClass().add("day-column");
            weekGrid.add(root, i, 0);

            DayColumn column = new DayColumn(root, name, total, list);
            add.setOnAction(e -> actions.create(column.date));
            installDropTarget(list, () -> column.date);
            columns.add(column);
        }
    }

    /** Acepta tareas arrastradas desde otra lista y las reprograma al día que indique {@code target}. */
    private void installDropTarget(ListView<Task> list, Supplier<LocalDate> target) {
        TaskDragAndDrop.installDropTarget(list, id -> actions.moveTo(id, target.get()));
    }

    @Override
    public void onShow() {
        LocalDate shown = monday;
        FxAsync.run(() -> {
            context.routines().ensureInstances(shown, shown.plusDays(6));
            return new WeekData(context.tasks().tasksForWeek(shown), context.tasks().unscheduled(),
                    context.reviews().prioritiesFor(shown));
        }, result -> {
            if (shown.equals(monday)) {
                data = result;
                apply(result);
            }
        });
    }

    private void apply(WeekData week) {
        LocalDate today = context.today();
        Area area = (Area) areaGroup.getSelectedToggle().getUserData();
        int open = 0;
        int done = 0;
        int minutes = 0;
        for (int i = 0; i < 7; i++) {
            DayColumn column = columns.get(i);
            column.date = monday.plusDays(i);
            List<Task> tasks = week.week().getOrDefault(column.date, List.of()).stream()
                    .filter(t -> area == null || t.getArea() == area).toList();
            column.list.getItems().setAll(tasks);
            column.name.setText(Formats.dayShort(column.date));
            int dayMinutes = tasks.stream().filter(Task::isOpen)
                    .mapToInt(t -> t.getEstimatedMinutes() == null ? 0 : t.getEstimatedMinutes()).sum();
            column.total.setText(dayMinutes == 0 ? " " : Formats.duration(dayMinutes));
            column.root.getStyleClass().removeAll("today", "past");
            if (column.date.equals(today)) {
                column.root.getStyleClass().add("today");
            } else if (column.date.isBefore(today)) {
                column.root.getStyleClass().add("past");
            }
            open += (int) tasks.stream().filter(Task::isOpen).count();
            done += (int) tasks.stream().filter(Task::isDone).count();
            minutes += dayMinutes;
        }
        unscheduledList.getItems().setAll(week.unscheduled().stream()
                .filter(t -> area == null || t.getArea() == area).toList());

        showPriorities(week.priorities());
        weekTitle.setText(Formats.weekTitle(monday));
        weekSubtitle.setText(open + (open == 1 ? " pendiente" : " pendientes") + " · " + done
                + (done == 1 ? " completada" : " completadas")
                + (minutes > 0 ? " · " + Formats.duration(minutes) + " planificadas" : ""));
    }

    /** Chips "1 Informe · 2 Sprint · 3 …" con las prioridades de la revisión semanal; doble clic para editar. */
    private void showPriorities(List<Task> priorities) {
        prioritiesBox.getChildren().clear();
        prioritiesBox.setVisible(!priorities.isEmpty());
        prioritiesBox.setManaged(!priorities.isEmpty());
        if (priorities.isEmpty()) {
            return;
        }
        Label title = new Label("Prioridades:", new FontIcon("mdi2s-star-outline"));
        title.getStyleClass().addAll("text-muted", "priorities-title");
        title.setMinWidth(Region.USE_PREF_SIZE);
        prioritiesBox.getChildren().add(title);
        for (int i = 0; i < priorities.size(); i++) {
            Task task = priorities.get(i);
            Label number = new Label(String.valueOf(i + 1));
            number.getStyleClass().add("priority-number");
            Label chip = new Label(task.getTitle(), number);
            chip.getStyleClass().add("priority-chip");
            chip.setMinWidth(40);
            chip.setMaxWidth(260);
            chip.pseudoClassStateChanged(DONE, task.isDone());
            chip.setTooltip(new Tooltip(task.getTitle() + (task.getScheduledDate() == null ? " · sin fecha"
                    : " · " + Formats.relativeDay(task.getScheduledDate(), context.today()))
                    + (task.isDone() ? " · hecha" : "") + "\nDoble clic para editarla"));
            chip.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    actions.edit(task);
                }
            });
            prioritiesBox.getChildren().add(chip);
        }
    }

    /** Muestra la semana que empieza en {@code weekMonday} (p. ej. desde la revisión semanal). */
    public void showWeek(LocalDate weekMonday) {
        monday = Formats.mondayOf(weekMonday);
        onShow();
    }

    @FXML
    private void openReview() {
        AppUi.navigator().navigate(View.REVISION);
    }

    @FXML
    private void previousWeek() {
        monday = monday.minusWeeks(1);
        onShow();
    }

    @FXML
    private void nextWeek() {
        monday = monday.plusWeeks(1);
        onShow();
    }

    @FXML
    private void thisWeek() {
        monday = Formats.mondayOf(context.today());
        onShow();
    }

    @FXML
    private void newTask() {
        LocalDate today = context.today();
        boolean currentWeek = !today.isBefore(monday) && !today.isAfter(monday.plusDays(6));
        actions.create(currentWeek ? today : monday);
    }
}
