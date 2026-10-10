package com.alejandro.synclife.controller;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.service.StatsService;
import com.alejandro.synclife.service.StatsService.AreaStats;
import com.alejandro.synclife.service.StatsService.DayStats;
import com.alejandro.synclife.service.StatsService.RoutineAdherence;
import com.alejandro.synclife.ui.Choice;
import com.alejandro.synclife.ui.Formats;
import com.alejandro.synclife.ui.FxAsync;
import com.alejandro.synclife.ui.Ui;
import com.alejandro.synclife.ui.ViewController;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;

/** Pantalla "Estadísticas": resumen del periodo, gráficos por área y por día, y cumplimiento de rutinas. */
public class StatsController implements ViewController {

    private enum Period {
        ESTA_SEMANA("Esta semana"),
        SEMANA_PASADA("Semana pasada"),
        ESTE_MES("Este mes"),
        ULTIMOS_30("Últimos 30 días");

        private final String label;

        Period(String label) {
            this.label = label;
        }
    }

    private final AppContext context;
    private Label completedValue;
    private Label completedDetail;
    private Label timeValue;
    private Label routinesValue;
    private Label routinesDetail;

    @FXML private Label periodLabel;
    @FXML private ComboBox<Choice<Period>> periodCombo;
    @FXML private HBox kpiBox;
    @FXML private VBox areaBars;
    @FXML private StackedBarChart<String, Number> dayChart;
    @FXML private TableView<RoutineAdherence> routineTable;

    public StatsController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        for (Period period : Period.values()) {
            periodCombo.getItems().add(Choice.of(period.label, period));
        }
        periodCombo.getSelectionModel().selectFirst();
        periodCombo.valueProperty().addListener((obs, old, value) -> onShow());

        completedValue = kpiValue();
        completedDetail = kpiDetail();
        timeValue = kpiValue();
        routinesValue = kpiValue();
        routinesDetail = kpiDetail();
        kpiBox.getChildren().addAll(
                kpiCard("Tareas completadas", completedValue, completedDetail),
                kpiCard("Tiempo invertido (estimado)", timeValue, kpiDetail("según la duración estimada")),
                kpiCard("Cumplimiento de rutinas", routinesValue, routinesDetail));

        TableColumn<RoutineAdherence, String> name = new TableColumn<>("Rutina");
        name.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().routine()));
        TableColumn<RoutineAdherence, Long> done = new TableColumn<>("Hechas");
        done.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().completed()));
        TableColumn<RoutineAdherence, Long> total = new TableColumn<>("Programadas");
        total.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().total()));
        TableColumn<RoutineAdherence, Double> rate = new TableColumn<>("Cumplimiento");
        rate.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().rate()));
        rate.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            private final ProgressBar bar = new ProgressBar();

            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    bar.setProgress(value);
                    bar.setPrefWidth(120);
                    setGraphic(bar);
                    setText(Math.round(value * 100) + " %");
                }
            }
        });
        routineTable.getColumns().setAll(List.of(name, done, total, rate));
        routineTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Label empty = new Label("Sin rutinas en este periodo");
        empty.getStyleClass().add("placeholder");
        routineTable.setPlaceholder(empty);
    }

    @Override
    public void onShow() {
        LocalDate today = context.today();
        LocalDate monday = Formats.mondayOf(today);
        LocalDate from;
        LocalDate to;
        switch (Ui.choiceValue(periodCombo)) {
            case SEMANA_PASADA -> {
                from = monday.minusWeeks(1);
                to = monday.minusDays(1);
            }
            case ESTE_MES -> {
                from = today.withDayOfMonth(1);
                to = today.withDayOfMonth(today.lengthOfMonth());
            }
            case ULTIMOS_30 -> {
                from = today.minusDays(29);
                to = today;
            }
            default -> {
                from = monday;
                to = monday.plusDays(6);
            }
        }
        LocalDate start = from;
        LocalDate end = to;
        FxAsync.run(() -> context.stats().compute(start, end), snapshot -> apply(snapshot, start, end));
    }

    private void apply(StatsService.Snapshot snapshot, LocalDate from, LocalDate to) {
        periodLabel.setText("Del " + Formats.dayMonth(from) + " al " + Formats.dayMonth(to));

        long completed = snapshot.completed();
        long total = snapshot.total();
        completedValue.setText(completed + " / " + total);
        completedDetail.setText(total == 0 ? "Sin tareas en el periodo" : Math.round(100.0 * completed / total) + " % del total");
        timeValue.setText(snapshot.minutesCompleted() == 0 ? "—" : Formats.duration((int) snapshot.minutesCompleted()));
        long routineDone = snapshot.routines().stream().mapToLong(RoutineAdherence::completed).sum();
        long routineTotal = snapshot.routines().stream().mapToLong(RoutineAdherence::total).sum();
        routinesValue.setText(routineTotal == 0 ? "—" : Math.round(100.0 * routineDone / routineTotal) + " %");
        routinesDetail.setText(routineTotal == 0 ? "Sin rutinas programadas" : routineDone + " de " + routineTotal + " días");

        areaBars.getChildren().clear();
        for (AreaStats area : snapshot.byArea()) {
            Label chip = Ui.areaChip(area.area());
            chip.setMinWidth(100);
            ProgressBar bar = new ProgressBar(area.rate());
            bar.setMaxWidth(Double.MAX_VALUE);
            bar.setStyle("-color-progress-bar-fill: " + area.area().getColorHex() + ";");
            HBox.setHgrow(bar, Priority.ALWAYS);
            String minutes = area.minutesCompleted() == 0 ? "" : " · " + Formats.duration((int) area.minutesCompleted());
            Label value = new Label(area.total() == 0 ? "Sin tareas" : area.completed() + " / " + area.total() + minutes);
            value.setMinWidth(110);
            value.getStyleClass().add("text-muted");
            HBox row = new HBox(12, chip, bar, value);
            row.getStyleClass().add("area-bar-row");
            areaBars.getChildren().add(row);
        }

        XYChart.Series<String, Number> doneSeries = new XYChart.Series<>();
        doneSeries.setName("Completadas");
        XYChart.Series<String, Number> openSeries = new XYChart.Series<>();
        openSeries.setName("Pendientes");
        boolean longPeriod = snapshot.byDay().size() > 10;
        for (DayStats day : snapshot.byDay()) {
            String label = longPeriod ? String.valueOf(day.day().getDayOfMonth()) : Formats.dayShort(day.day());
            doneSeries.getData().add(new XYChart.Data<>(label, day.completed()));
            openSeries.getData().add(new XYChart.Data<>(label, day.open()));
        }
        dayChart.getData().setAll(List.of(doneSeries, openSeries));

        routineTable.getItems().setAll(snapshot.routines());
    }

    private static VBox kpiCard(String title, Label value, Label detail) {
        Label heading = new Label(title);
        heading.getStyleClass().add("summary-title");
        VBox card = new VBox(4, heading, value, detail);
        card.getStyleClass().add("summary-card");
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private static Label kpiValue() {
        Label label = new Label("—");
        label.getStyleClass().add("summary-count");
        return label;
    }

    private static Label kpiDetail() {
        return kpiDetail("");
    }

    private static Label kpiDetail(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("text-muted");
        return label;
    }
}
