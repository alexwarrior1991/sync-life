package com.alejandro.synclife.controller;

import atlantafx.base.controls.RingProgressIndicator;
import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.TaskStatus;
import com.alejandro.synclife.ui.Formats;
import com.alejandro.synclife.ui.FxAsync;
import com.alejandro.synclife.ui.TaskActions;
import com.alejandro.synclife.ui.Ui;
import com.alejandro.synclife.ui.ViewController;
import com.alejandro.synclife.ui.cell.TaskListCell;
import com.alejandro.synclife.ui.control.TimeField;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Pantalla "Hoy": agenda del día, resumen por área, alta rápida y pendientes arrastradas. */
public class TodayController implements ViewController {

    private record DayData(List<Task> day, List<Task> carryOver, List<Task> dueSoon) {
    }

    /** Tarjeta de resumen de un área. */
    private record AreaCard(Label count, ProgressBar progress, Label minutes) {
    }

    private final AppContext context;
    private final Map<Area, AreaCard> areaCards = new EnumMap<>(Area.class);
    private TaskActions actions;
    private LocalDate day;
    private List<Task> dayTasks = List.of();
    private RingProgressIndicator overallRing;
    private Label overallLabel;

    @FXML private Label dayTitle;
    @FXML private Label daySubtitle;
    @FXML private DatePicker datePicker;
    @FXML private HBox summaryBox;
    @FXML private ComboBox<Area> quickArea;
    @FXML private TextField quickTitle;
    @FXML private TimeField quickTime;
    @FXML private Label agendaCount;
    @FXML private CheckBox showDone;
    @FXML private ListView<Task> agendaList;
    @FXML private VBox sideColumn;
    @FXML private Button moveAllButton;
    @FXML private ListView<Task> carryList;
    @FXML private ListView<Task> dueList;

    public TodayController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        day = context.today();
        actions = new TaskActions(context, agendaList, this::onShow);

        agendaList.setCellFactory(list -> new TaskListCell(actions, false));
        carryList.setCellFactory(list -> new TaskListCell(actions, false));
        dueList.setCellFactory(list -> new TaskListCell(actions, false));
        for (ListView<Task> list : List.of(agendaList, carryList, dueList)) {
            actions.installDeleteKey(list);
        }
        agendaList.setPlaceholder(placeholder("Nada planificado para este día.\nAñade una tarea con el campo de arriba."));
        carryList.setPlaceholder(placeholder("Todo al día"));
        dueList.setPlaceholder(placeholder("Sin fechas límite próximas"));

        Ui.limitLength(quickTitle, 200);
        quickArea.getItems().setAll(Area.values());
        quickArea.setValue(Area.TRABAJO);

        datePicker.setValue(day);
        datePicker.valueProperty().addListener((obs, old, value) -> {
            if (value != null && !value.equals(day)) {
                day = value;
                onShow();
            }
        });
        showDone.selectedProperty().addListener((obs, old, value) -> applyAgendaFilter());

        buildSummaryCards();
    }

    @Override
    public void onShow() {
        LocalDate shown = day;
        LocalDate today = context.today();
        boolean isToday = shown.equals(today);
        FxAsync.run(() -> {
            if (!shown.isBefore(today)) {
                context.routines().ensureInstances(shown, shown); // por si el día está más allá del horizonte
            }
            return new DayData(
                    context.tasks().tasksForDay(shown),
                    isToday ? context.tasks().carryOver() : List.of(),
                    isToday ? context.tasks().dueSoon(7) : List.of());
        }, data -> {
            if (shown.equals(day)) {
                apply(data, isToday);
            }
        });
    }

    private void apply(DayData data, boolean isToday) {
        LocalDate today = context.today();
        dayTitle.setText(Formats.dayTitle(day));
        String relative = Formats.relativeDay(day, today);
        long open = data.day().stream().filter(Task::isOpen).count();
        int plannedMinutes = data.day().stream().filter(Task::isOpen)
                .mapToInt(t -> t.getEstimatedMinutes() == null ? 0 : t.getEstimatedMinutes()).sum();
        String subtitle = (relative.length() <= 6 ? relative + " · " : "")
                + (open == 0 ? "Sin tareas pendientes" : open + (open == 1 ? " tarea pendiente" : " tareas pendientes"))
                + (plannedMinutes > 0 ? " · " + Formats.duration(plannedMinutes) + " planificadas" : "");
        daySubtitle.setText(subtitle);
        datePicker.setValue(day);

        dayTasks = data.day();
        applyAgendaFilter();
        updateSummary();

        carryList.getItems().setAll(data.carryOver());
        dueList.getItems().setAll(data.dueSoon());
        moveAllButton.setDisable(data.carryOver().isEmpty());
        sideColumn.setVisible(isToday);
        sideColumn.setManaged(isToday);
    }

    private void applyAgendaFilter() {
        List<Task> visible = showDone.isSelected()
                ? dayTasks
                : dayTasks.stream().filter(Task::isOpen).toList();
        agendaList.getItems().setAll(visible);
        long done = dayTasks.stream().filter(Task::isDone).count();
        long total = dayTasks.stream().filter(t -> t.getStatus() != TaskStatus.CANCELADA).count();
        agendaCount.setText(total == 0 ? "" : done + " de " + total + " hechas");
    }

    // ---------------------------------------------------------------- resumen

    private void buildSummaryCards() {
        for (Area area : Area.values()) {
            FontIcon icon = new FontIcon(area.getIconLiteral());
            icon.setIconColor(Color.web(area.getColorHex()));
            Label name = new Label(area.getDisplayName(), icon);
            name.getStyleClass().add("summary-title");
            Label count = new Label();
            count.getStyleClass().add("summary-count");
            ProgressBar progress = new ProgressBar(0);
            progress.setMaxWidth(Double.MAX_VALUE);
            progress.setStyle("-color-progress-bar-fill: " + area.getColorHex() + ";");
            Label minutes = new Label();
            minutes.getStyleClass().add("text-muted");
            VBox card = new VBox(6, name, count, progress, minutes);
            card.getStyleClass().add("summary-card");
            HBox.setHgrow(card, Priority.ALWAYS);
            card.setMaxWidth(Double.MAX_VALUE);
            summaryBox.getChildren().add(card);
            areaCards.put(area, new AreaCard(count, progress, minutes));
        }
        overallRing = new RingProgressIndicator(0);
        overallRing.setMinSize(64, 64);
        overallRing.setPrefSize(64, 64);
        overallLabel = new Label();
        overallLabel.getStyleClass().add("text-muted");
        Label title = new Label("Progreso del día");
        title.getStyleClass().add("summary-title");
        VBox text = new VBox(4, title, overallLabel);
        text.setAlignment(Pos.CENTER_LEFT);
        HBox card = new HBox(12, overallRing, text);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("summary-card");
        card.setMinWidth(220);
        summaryBox.getChildren().add(card);
    }

    private void updateSummary() {
        long allDone = 0;
        long allTotal = 0;
        for (Area area : Area.values()) {
            List<Task> tasks = dayTasks.stream()
                    .filter(t -> t.getArea() == area && t.getStatus() != TaskStatus.CANCELADA).toList();
            long done = tasks.stream().filter(Task::isDone).count();
            int minutes = tasks.stream().mapToInt(t -> t.getEstimatedMinutes() == null ? 0 : t.getEstimatedMinutes()).sum();
            AreaCard card = areaCards.get(area);
            card.count().setText(tasks.isEmpty() ? "—" : done + " / " + tasks.size());
            card.progress().setProgress(tasks.isEmpty() ? 0 : (double) done / tasks.size());
            card.minutes().setText(minutes == 0 ? (tasks.isEmpty() ? "Sin tareas" : "Sin estimación")
                    : Formats.duration(minutes) + " en total");
            allDone += done;
            allTotal += tasks.size();
        }
        overallRing.setProgress(allTotal == 0 ? 0 : (double) allDone / allTotal);
        overallLabel.setText(allTotal == 0 ? "Día libre" : allDone + " de " + allTotal + " completadas");
    }

    // ---------------------------------------------------------------- acciones

    @FXML
    private void previousDay() {
        day = day.minusDays(1);
        onShow();
    }

    @FXML
    private void nextDay() {
        day = day.plusDays(1);
        onShow();
    }

    @FXML
    private void goToday() {
        day = context.today();
        onShow();
    }

    @FXML
    private void newTask() {
        actions.create(day);
    }

    @FXML
    private void quickAdd() {
        String title = quickTitle.getText();
        if (title == null || title.isBlank() || !quickTime.isValid()) {
            quickTitle.requestFocus();
            return;
        }
        Area area = quickArea.getValue();
        LocalDate date = day;
        LocalTime time = quickTime.getTime();
        FxAsync.run(() -> context.tasks().quickAdd(title, area, date, time), saved -> {
            quickTitle.clear();
            quickTime.clear();
            quickTitle.requestFocus();
            onShow();
        });
    }

    @FXML
    private void moveAllToToday() {
        List<Long> ids = carryList.getItems().stream().map(Task::getId).toList();
        LocalDate today = context.today();
        FxAsync.run(() -> context.tasks().rescheduleAll(ids, today), moved -> onShow());
    }

    private static Label placeholder(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("placeholder");
        label.setWrapText(true);
        return label;
    }
}
