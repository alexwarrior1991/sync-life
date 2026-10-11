package com.alejandro.synclife.controller;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.service.StatsService;
import com.alejandro.synclife.service.StatsService.AreaStats;
import com.alejandro.synclife.service.StatsService.DayStats;
import com.alejandro.synclife.service.StatsService.Deviation;
import com.alejandro.synclife.service.StatsService.EstimateAccuracy;
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
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

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
    private Label timeDetail;
    private Label accuracyValue;
    private Label accuracyDetail;
    private Label routinesValue;
    private Label routinesDetail;

    @FXML private Label periodLabel;
    @FXML private ComboBox<Choice<Period>> periodCombo;
    @FXML private HBox kpiBox;
    @FXML private VBox areaBars;
    @FXML private StackedBarChart<String, Number> dayChart;
    @FXML private Label accuracyHint;
    @FXML private TableView<Deviation> accuracyTable;
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
        timeDetail = kpiDetail();
        accuracyValue = kpiValue();
        accuracyDetail = kpiDetail();
        routinesValue = kpiValue();
        routinesDetail = kpiDetail();
        kpiBox.getChildren().addAll(
                kpiCard("Tareas completadas", completedValue, completedDetail),
                kpiCard("Tiempo registrado", timeValue, timeDetail),
                kpiCard("Precisión de estimaciones", accuracyValue, accuracyDetail),
                kpiCard("Cumplimiento de rutinas", routinesValue, routinesDetail));
        buildAccuracyTable();

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

    private void buildAccuracyTable() {
        TableColumn<Deviation, String> title = new TableColumn<>("Tarea");
        title.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().title()));
        title.setPrefWidth(320);
        TableColumn<Deviation, Area> area = new TableColumn<>("Área");
        area.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().area()));
        area.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Area value, boolean empty) {
                super.updateItem(value, empty);
                setText(null);
                setGraphic(empty || value == null ? null : Ui.areaChip(value));
            }
        });
        area.setPrefWidth(120);
        TableColumn<Deviation, String> estimated = new TableColumn<>("Estimado");
        estimated.setCellValueFactory(cd -> new ReadOnlyStringWrapper(Formats.duration(cd.getValue().estimatedMinutes())));
        TableColumn<Deviation, String> real = new TableColumn<>("Real");
        real.setCellValueFactory(cd -> new ReadOnlyStringWrapper(Formats.duration(cd.getValue().trackedMinutes())));
        TableColumn<Deviation, Deviation> deviation = new TableColumn<>("Desviación");
        deviation.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        deviation.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Deviation value, boolean empty) {
                super.updateItem(value, empty);
                getStyleClass().removeAll("over", "on-target");
                if (empty || value == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                long percent = Math.round((value.ratio() - 1) * 100);
                setText(signed(percent));
                FontIcon icon = new FontIcon(value.onTarget() ? "mdi2c-check-circle-outline"
                        : percent > 0 ? "mdi2t-trending-up" : "mdi2t-trending-down");
                setGraphic(icon);
                setTooltip(new javafx.scene.control.Tooltip(value.onTarget() ? "Dentro del margen"
                        : percent > 0 ? "Llevó más de lo estimado" : "Llevó menos de lo estimado"));
                getStyleClass().add(value.onTarget() ? "on-target" : "over");
            }
        });
        accuracyTable.getColumns().setAll(List.of(title, area, estimated, real, deviation));
        accuracyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Label empty = new Label("Completa tareas con duración estimada y registra su tiempo (Pomodoro o a mano) "
                + "para comparar.");
        empty.getStyleClass().add("placeholder");
        empty.setWrapText(true);
        accuracyTable.setPlaceholder(empty);
    }

    private static String signed(long percent) {
        return (percent > 0 ? "+" : "") + percent + " %";
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
        timeValue.setText(snapshot.minutesTracked() == 0 ? "—" : Formats.duration((int) snapshot.minutesTracked()));
        timeDetail.setText(snapshot.minutesCompleted() == 0 ? "Pomodoro y apuntes manuales"
                : Formats.duration((int) snapshot.minutesCompleted()) + " estimadas en lo completado");
        EstimateAccuracy accuracy = snapshot.accuracy();
        accuracyValue.setText(accuracy.tasks() == 0 ? "—" : Math.round(accuracy.onTargetRate() * 100) + " %");
        accuracyDetail.setText(accuracy.tasks() == 0 ? "Sin tareas con tiempo registrado"
                : accuracy.onTarget() + " de " + accuracy.tasks() + " en ±"
                + Math.round(StatsService.ACCURACY_TOLERANCE * 100) + " %");
        accuracyHint.setText(accuracy.tasks() == 0
                ? "Tareas completadas en el periodo con duración estimada y tiempo registrado."
                : "En conjunto has dedicado " + Formats.duration((int) accuracy.trackedMinutes()) + " a lo que estimaste en "
                + Formats.duration((int) accuracy.estimatedMinutes()) + " (" + ratioText(accuracy.overallRatio()) + "). "
                + accuracy.over() + (accuracy.over() == 1 ? " se alargó" : " se alargaron") + " y "
                + accuracy.under() + (accuracy.under() == 1 ? " fue más rápida" : " fueron más rápidas")
                + " de lo previsto (margen ±" + Math.round(StatsService.ACCURACY_TOLERANCE * 100) + " %). "
                + "Abajo, las mayores desviaciones.");
        accuracyTable.getItems().setAll(accuracy.largest());
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
            String minutes = area.minutesTracked() == 0 ? "" : " · " + Formats.duration((int) area.minutesTracked());
            Label value = new Label(area.total() == 0 ? "Sin tareas" : area.completed() + " / " + area.total() + minutes);
            if (area.minutesTracked() > 0) {
                value.setTooltip(new javafx.scene.control.Tooltip("Tiempo real registrado en el periodo"));
            }
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

    /** "un 12 % más", "un 8 % menos" o "justo lo previsto". */
    private static String ratioText(double ratio) {
        long percent = Math.round((ratio - 1) * 100);
        if (percent == 0) {
            return "justo lo previsto";
        }
        return "un " + Math.abs(percent) + " % " + (percent > 0 ? "más" : "menos");
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
