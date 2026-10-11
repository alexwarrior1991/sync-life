package com.alejandro.synclife.controller;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.entity.WeeklyReview;
import com.alejandro.synclife.model.enums.TaskStatus;
import com.alejandro.synclife.service.GoalService.GoalProgress;
import com.alejandro.synclife.service.WeeklyReviewService;
import com.alejandro.synclife.service.WeeklyReviewService.Summary;
import com.alejandro.synclife.ui.AppUi;
import com.alejandro.synclife.ui.Choice;
import com.alejandro.synclife.ui.Dialogs;
import com.alejandro.synclife.ui.Formats;
import com.alejandro.synclife.ui.FxAsync;
import com.alejandro.synclife.ui.TaskActions;
import com.alejandro.synclife.ui.TaskDragAndDrop;
import com.alejandro.synclife.ui.Ui;
import com.alejandro.synclife.ui.View;
import com.alejandro.synclife.ui.ViewController;
import com.alejandro.synclife.ui.cell.GoalListCell;
import com.alejandro.synclife.ui.cell.TaskListCell;
import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * Pantalla "Revisión semanal": un asistente de cinco pasos para repasar la semana que termina y planificar
 * la siguiente. Como las pantallas se crean una vez y se reutilizan, una revisión a medias (notas,
 * prioridades) se conserva al ir a otra pantalla y volver.
 */
public class ReviewController implements ViewController {

    private record ReviewData(LocalDate planned, Summary summary, Optional<WeeklyReview> review,
                              List<Task> priorities, List<Task> candidates, Map<LocalDate, List<Task>> week,
                              List<Task> unscheduled, List<WeeklyReview> history) {
    }

    /** Columna de un día en el paso "Planificar". */
    private record DayBox(VBox root, Label name, Label total, ListView<Task> list) {
    }

    private static final String[] STEP_NAMES = {"Repaso", "Pendientes", "Planificar", "Prioridades", "Notas"};
    private static final String[] STEP_ICONS = {"mdi2h-history", "mdi2i-inbox-outline", "mdi2c-calendar-week",
            "mdi2s-star-outline", "mdi2n-note-text-outline"};
    private static final String[] STEP_HINTS = {
            "Mira cómo fue la semana: qué completaste, el tiempo que registraste y cómo van tus objetivos.",
            "Decide qué hacer con lo que quedó pendiente: pásalo al lunes, quítale la fecha, márcalo como hecho o cancélalo.",
            "Arrastra las tareas sin fecha a un día. Cada día muestra cuánto tienes ya planificado.",
            "Elige hasta tres tareas que harán que la semana merezca la pena. Aparecerán en la vista Semana.",
            "Apunta lo que quieras recordar de esta semana y guarda la revisión."};
    private static final PseudoClass CURRENT = PseudoClass.getPseudoClass("current");
    private static final PseudoClass DONE = PseudoClass.getPseudoClass("done");

    private final AppContext context;
    private final ToggleGroup weekGroup = new ToggleGroup();
    private final List<Button> stepButtons = new ArrayList<>();
    private final Node[] steps = new Node[STEP_NAMES.length];
    private final List<DayBox> dayBoxes = new ArrayList<>(7);
    private final List<ComboBox<Choice<Task>>> priorityCombos = new ArrayList<>();
    private TaskActions actions;
    private int step;
    private LocalDate planned;
    private ReviewData data;
    /** Hay notas o prioridades sin guardar: al recargar no se sustituyen por lo guardado. */
    private boolean dirty;
    private boolean filling;

    // Paso 1: repaso
    private final Label doneValue = kpiValue();
    private final Label pendingValue = kpiValue();
    private final Label timeValue = kpiValue();
    private final Label routinesValue = kpiValue();
    private final Label routinesDetail = kpiDetail("");
    private final ListView<Task> doneList = new ListView<>();
    private final ListView<GoalProgress> goalsList = new ListView<>();
    // Paso 2: pendientes
    private final Label pendingTitle = new Label();
    private final ListView<Task> pendingList = new ListView<>();
    // Paso 3: planificar
    private final ListView<Task> unscheduledList = new ListView<>();
    // Paso 5: notas
    private final TextArea notesArea = new TextArea();
    private final Label saveSummary = new Label();
    private final Label savedLabel = new Label();

    @FXML private Label subtitle;
    @FXML private HBox weekChoiceBox;
    @FXML private HBox stepBar;
    @FXML private StackPane stepHost;
    @FXML private Button backButton;
    @FXML private Label stepHint;
    @FXML private Button nextButton;
    @FXML private ListView<WeeklyReview> historyList;

    public ReviewController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        planned = context.reviews().defaultPlannedMonday();
        actions = new TaskActions(context, stepHost, this::load);
        buildWeekChoice();
        buildStepBar();
        steps[0] = buildOverview();
        steps[1] = buildPending();
        steps[2] = buildPlanning();
        steps[3] = buildPriorities();
        steps[4] = buildNotes();
        historyList.setCellFactory(list -> new HistoryCell());
        historyList.setPlaceholder(placeholder("Aún no has guardado ninguna revisión."));
        historyList.setOnMouseClicked(e -> {
            WeeklyReview review = historyList.getSelectionModel().getSelectedItem();
            if (review != null && !review.getWeekStart().equals(planned)) {
                plan(review.getWeekStart());
            }
        });
        goToStep(0);
    }

    // ---------------------------------------------------------------- construcción

    private void buildWeekChoice() {
        LocalDate thisMonday = Formats.mondayOf(context.today());
        ToggleButton current = new ToggleButton("Planificar esta semana");
        current.getStyleClass().add("left-pill");
        current.setUserData(thisMonday);
        ToggleButton next = new ToggleButton("La próxima");
        next.getStyleClass().add("right-pill");
        next.setUserData(thisMonday.plusWeeks(1));
        for (ToggleButton button : List.of(current, next)) {
            button.setToggleGroup(weekGroup);
            button.setOnAction(e -> plan((LocalDate) button.getUserData()));
            weekChoiceBox.getChildren().add(button);
        }
    }

    private void buildStepBar() {
        for (int i = 0; i < STEP_NAMES.length; i++) {
            int index = i;
            Button button = new Button((i + 1) + ". " + STEP_NAMES[i], new FontIcon(STEP_ICONS[i]));
            button.getStyleClass().addAll("flat", "step-button");
            button.setOnAction(e -> goToStep(index));
            button.setMinWidth(Button.USE_PREF_SIZE);
            stepButtons.add(button);
            stepBar.getChildren().add(button);
        }
    }

    private Node buildOverview() {
        HBox kpis = new HBox(12,
                kpiCard("Completadas", doneValue, kpiDetail("tareas puntuales")),
                kpiCard("Pendientes", pendingValue, kpiDetail("de esa semana o antes")),
                kpiCard("Tiempo registrado", timeValue, kpiDetail("Pomodoro y apuntes")),
                kpiCard("Rutinas", routinesValue, routinesDetail));
        doneList.setCellFactory(list -> new TaskListCell(actions, false));
        doneList.setPlaceholder(placeholder("No completaste tareas puntuales esa semana."));
        goalsList.setCellFactory(list -> new GoalListCell(context::today));
        goalsList.setPlaceholder(placeholder("Sin objetivos en curso. Créalos en «Objetivos»."));
        VBox done = panel("Lo que hiciste", doneList);
        VBox goals = panel("Objetivos en curso", goalsList);
        HBox lists = new HBox(12, done, goals);
        HBox.setHgrow(done, Priority.ALWAYS);
        HBox.setHgrow(goals, Priority.ALWAYS);
        done.setPrefWidth(400);
        goals.setPrefWidth(300);
        VBox.setVgrow(lists, Priority.ALWAYS);
        return new VBox(12, kpis, lists);
    }

    private Node buildPending() {
        pendingList.setCellFactory(list -> new TaskListCell(actions, false));
        pendingList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        pendingList.setPlaceholder(placeholder("Nada pendiente. ¡Semana cerrada!"));
        actions.installDeleteKey(pendingList);
        Button toMonday = actionButton("Al lunes", "mdi2c-calendar-arrow-right",
                ids -> () -> context.tasks().rescheduleAll(ids, planned));
        Button unschedule = actionButton("Quitar fecha", "mdi2c-calendar-blank-outline",
                ids -> () -> context.tasks().rescheduleAll(ids, null));
        Button done = actionButton("Hecha", "mdi2c-check", ids -> () -> setStatus(ids, TaskStatus.COMPLETADA));
        Button cancel = actionButton("Cancelar", "mdi2c-cancel", ids -> () -> setStatus(ids, TaskStatus.CANCELADA));
        Button allToMonday = new Button("Todas al lunes", new FontIcon("mdi2c-check-all"));
        allToMonday.getStyleClass().add("accent");
        allToMonday.setOnAction(e -> {
            List<Long> ids = pendingList.getItems().stream().map(Task::getId).toList();
            run(() -> context.tasks().rescheduleAll(ids, planned));
        });
        allToMonday.disableProperty().bind(Bindings.isEmpty(pendingList.getItems()));
        Label hint = new Label("Selecciona una o varias (Ctrl/Mayús + clic) y elige qué hacer con ellas:");
        hint.getStyleClass().addAll("text-muted", "text-small");
        for (Button button : List.of(toMonday, unschedule, done, cancel, allToMonday)) {
            button.setMinWidth(Region.USE_PREF_SIZE);
        }
        HBox bar = new HBox(8, toMonday, unschedule, done, cancel, Ui.hSpacer(), allToMonday);
        bar.setAlignment(Pos.CENTER_LEFT);
        pendingTitle.getStyleClass().add("title-4");
        VBox box = new VBox(10, pendingTitle, hint, bar, pendingList);
        box.getStyleClass().add("panel");
        VBox.setVgrow(pendingList, Priority.ALWAYS);
        return box;
    }

    private Node buildPlanning() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        for (int c = 0; c < 4; c++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(25);
            grid.getColumnConstraints().add(column);
        }
        for (int r = 0; r < 2; r++) {
            RowConstraints row = new RowConstraints();
            row.setVgrow(Priority.ALWAYS);
            row.setPercentHeight(50);
            grid.getRowConstraints().add(row);
        }
        for (int i = 0; i < 7; i++) {
            Label name = new Label();
            name.getStyleClass().add("day-name");
            Label total = new Label();
            total.getStyleClass().addAll("text-muted", "text-small");
            ListView<Task> list = new ListView<>();
            list.getStyleClass().add("day-list");
            list.setCellFactory(l -> new TaskListCell(actions, true));
            actions.installDeleteKey(list);
            int offset = i;
            TaskDragAndDrop.installDropTarget(list, id -> actions.moveTo(id, planned.plusDays(offset)));
            VBox.setVgrow(list, Priority.ALWAYS);
            VBox root = new VBox(4, new VBox(0, name, total), list);
            root.getStyleClass().add("day-column");
            root.setMinHeight(0);
            grid.add(root, i % 4, i / 4);
            dayBoxes.add(new DayBox(root, name, total, list));
        }
        unscheduledList.setCellFactory(l -> new TaskListCell(actions, true));
        unscheduledList.setPlaceholder(placeholder("Todo tiene fecha"));
        TaskDragAndDrop.installDropTarget(unscheduledList, id -> actions.moveTo(id, null));
        VBox.setVgrow(unscheduledList, Priority.ALWAYS);
        Label title = new Label("Sin fecha");
        title.getStyleClass().add("day-name");
        Label hint = new Label("Arrástralas a un día");
        hint.getStyleClass().addAll("text-muted", "text-small");
        VBox unscheduled = new VBox(4, new VBox(0, title, hint), unscheduledList);
        unscheduled.getStyleClass().addAll("day-column", "unscheduled-column");
        unscheduled.setMinHeight(0);
        grid.add(unscheduled, 3, 1);
        return grid;
    }

    private Node buildPriorities() {
        VBox rows = new VBox(14);
        for (int i = 0; i < WeeklyReviewService.MAX_PRIORITIES; i++) {
            Label number = new Label(String.valueOf(i + 1));
            number.getStyleClass().add("priority-number");
            number.setMinWidth(Label.USE_PREF_SIZE);
            ComboBox<Choice<Task>> combo = new ComboBox<>();
            combo.setMaxWidth(Double.MAX_VALUE);
            combo.setPromptText("Elige una tarea…");
            combo.valueProperty().addListener((obs, old, value) -> {
                if (!filling) {
                    dirty = true;
                    updateSaveSummary();
                }
            });
            HBox.setHgrow(combo, Priority.ALWAYS);
            HBox row = new HBox(12, number, combo);
            row.setAlignment(Pos.CENTER_LEFT);
            rows.getChildren().add(row);
            priorityCombos.add(combo);
        }
        Label hint = new Label("Solo tareas puntuales abiertas de la semana o sin fecha. Las rutinas no cuentan: "
                + "ya se repiten solas. Deja un hueco vacío si con menos basta.");
        hint.setWrapText(true);
        hint.getStyleClass().addAll("text-muted", "text-small");
        Label title = new Label("Las tres prioridades de la semana");
        title.getStyleClass().add("title-4");
        VBox box = new VBox(16, title, rows, hint);
        box.getStyleClass().add("panel");
        box.setMaxWidth(760);
        box.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(box, Pos.TOP_LEFT);
        return new StackPane(box);
    }

    private Node buildNotes() {
        notesArea.setId("review-notes");
        notesArea.setPromptText("¿Qué salió bien? ¿Qué cambiarías? ¿Algo que no quieras olvidar la semana que viene?");
        notesArea.setWrapText(true);
        notesArea.setPrefRowCount(8);
        Ui.limitLength(notesArea, WeeklyReviewService.MAX_NOTES);
        notesArea.textProperty().addListener((obs, old, value) -> {
            if (!filling) {
                dirty = true;
            }
        });
        saveSummary.setWrapText(true);
        savedLabel.getStyleClass().addAll("text-muted", "text-small");
        Button showWeek = new Button("Ver la semana planificada", new FontIcon("mdi2c-calendar-week"));
        showWeek.getStyleClass().add("flat");
        showWeek.setOnAction(e -> openWeek());
        Label title = new Label("Notas de la semana");
        title.getStyleClass().add("title-4");
        VBox box = new VBox(12, title, notesArea, saveSummary, new HBox(8, savedLabel, Ui.hSpacer(), showWeek));
        box.getStyleClass().add("panel");
        box.setMaxWidth(760);
        box.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(box, Pos.TOP_LEFT);
        return new StackPane(box);
    }

    // ---------------------------------------------------------------- datos

    @Override
    public void onShow() {
        load();
    }

    /** Cambia la semana que se planifica (y descarta lo no guardado de la anterior). */
    private void plan(LocalDate monday) {
        if (monday.equals(planned)) {
            return;
        }
        if (dirty && !Dialogs.confirm("¿Cambiar de semana?",
                "Las notas y prioridades sin guardar de esta revisión se perderán.", "Cambiar")) {
            selectWeekChoice();
            return;
        }
        planned = monday;
        dirty = false;
        goToStep(0);
        load();
    }

    private void load() {
        LocalDate week = planned;
        FxAsync.run(() -> {
            context.routines().ensureInstances(week, week.plusDays(6));
            WeeklyReviewService reviews = context.reviews();
            return new ReviewData(week, reviews.summarize(week.minusWeeks(1)), reviews.find(week),
                    reviews.prioritiesFor(week), reviews.priorityCandidates(week), context.tasks().tasksForWeek(week),
                    context.tasks().unscheduled(), reviews.history(26));
        }, result -> {
            if (result.planned().equals(planned)) {
                apply(result);
            }
        });
    }

    private void apply(ReviewData result) {
        data = result;
        Summary summary = result.summary();
        LocalDate today = context.today();
        subtitle.setText("Repasa la semana del " + Formats.dayMonth(summary.reviewedMonday()) + " al "
                + Formats.dayMonth(summary.reviewedSunday()) + " y planifica la del " + Formats.dayMonth(planned)
                + " al " + Formats.dayMonth(planned.plusDays(6))
                + (result.review().isPresent() ? " · revisión guardada" : ""));
        selectWeekChoice();

        // Paso 1
        doneValue.setText(String.valueOf(summary.completed().size()));
        pendingValue.setText(String.valueOf(summary.pending().size()));
        timeValue.setText(summary.minutesTracked() == 0 ? "—" : Formats.duration((int) summary.minutesTracked()));
        routinesValue.setText(summary.routineTotal() == 0 ? "—"
                : Math.round(100.0 * summary.routineDone() / summary.routineTotal()) + " %");
        routinesDetail.setText(summary.routineTotal() == 0 ? "sin rutinas"
                : summary.routineDone() + " de " + summary.routineTotal() + " días");
        doneList.getItems().setAll(summary.completed());
        goalsList.getItems().setAll(summary.goals());

        // Paso 2
        pendingList.getItems().setAll(summary.pending());
        int pending = summary.pending().size();
        pendingTitle.setText(pending == 0 ? "Sin pendientes"
                : pending + (pending == 1 ? " tarea pendiente" : " tareas pendientes") + " antes del "
                + Formats.dayMonth(planned));

        // Paso 3
        for (int i = 0; i < 7; i++) {
            LocalDate day = planned.plusDays(i);
            DayBox box = dayBoxes.get(i);
            List<Task> tasks = result.week().getOrDefault(day, List.of());
            box.list().getItems().setAll(tasks);
            box.name().setText(Formats.dayShort(day));
            long open = tasks.stream().filter(Task::isOpen).count();
            int minutes = tasks.stream().filter(Task::isOpen)
                    .mapToInt(t -> t.getEstimatedMinutes() == null ? 0 : t.getEstimatedMinutes()).sum();
            box.total().setText(open == 0 ? "Libre" : open + (open == 1 ? " tarea" : " tareas")
                    + (minutes > 0 ? " · " + Formats.duration(minutes) : ""));
            box.root().getStyleClass().removeAll("today", "past");
            if (day.equals(today)) {
                box.root().getStyleClass().add("today");
            } else if (day.isBefore(today)) {
                box.root().getStyleClass().add("past");
            }
        }
        unscheduledList.getItems().setAll(result.unscheduled());

        // Pasos 4 y 5 (sin pisar lo que el usuario está editando)
        filling = true;
        try {
            List<Long> chosen = dirty ? selectedPriorityIds() : result.priorities().stream().map(Task::getId).toList();
            List<Task> options = new ArrayList<>(result.candidates());
            for (Task priority : result.priorities()) {
                if (options.stream().noneMatch(t -> t.getId().equals(priority.getId()))) {
                    options.add(priority); // una prioridad ya hecha también se muestra
                }
            }
            for (int i = 0; i < priorityCombos.size(); i++) {
                ComboBox<Choice<Task>> combo = priorityCombos.get(i);
                combo.getItems().setAll(Choice.of("— Sin prioridad —", null));
                for (Task task : options) {
                    combo.getItems().add(Choice.of(describe(task, today), task));
                }
                Long id = i < chosen.size() ? chosen.get(i) : null;
                combo.getItems().stream().filter(c -> c.value() != null && c.value().getId().equals(id))
                        .findFirst().ifPresentOrElse(combo::setValue, () -> combo.getSelectionModel().selectFirst());
            }
            if (!dirty) {
                notesArea.setText(result.review().map(WeeklyReview::getNotes).orElse(""));
            }
        } finally {
            filling = false;
        }
        savedLabel.setText(result.review().map(r -> "Guardada el " + Formats.date(r.getUpdatedAt().toLocalDate())
                + " a las " + Formats.time(r.getUpdatedAt().toLocalTime())).orElse("Aún no se ha guardado"));
        updateSaveSummary();

        historyList.getItems().setAll(result.history());
        historyList.refresh();
    }

    private void updateSaveSummary() {
        long count = priorityCombos.stream().filter(c -> c.getValue() != null && c.getValue().value() != null).count();
        saveSummary.setText("Se guardará la revisión de la " + Formats.weekTitle(planned).toLowerCase() + " con "
                + (count == 0 ? "ninguna prioridad" : count == 1 ? "1 prioridad" : count + " prioridades")
                + ". Las prioridades aparecerán en la vista Semana.");
    }

    // ---------------------------------------------------------------- navegación entre pasos

    /** Muestra el paso {@code index} (0–4). */
    public void goToStep(int index) {
        step = Math.max(0, Math.min(STEP_NAMES.length - 1, index));
        stepHost.getChildren().setAll(steps[step]);
        for (int i = 0; i < stepButtons.size(); i++) {
            stepButtons.get(i).pseudoClassStateChanged(CURRENT, i == step);
            stepButtons.get(i).pseudoClassStateChanged(DONE, i < step);
        }
        backButton.setDisable(step == 0);
        boolean last = step == STEP_NAMES.length - 1;
        nextButton.setText(last ? "Guardar revisión" : "Siguiente: " + STEP_NAMES[step + 1]);
        nextButton.setGraphic(new FontIcon(last ? "mdi2c-content-save-outline" : "mdi2a-arrow-right"));
        stepHint.setText(STEP_HINTS[step]);
    }

    public int currentStep() {
        return step;
    }

    @FXML
    private void previous() {
        goToStep(step - 1);
    }

    @FXML
    private void next() {
        if (step == STEP_NAMES.length - 1) {
            save();
        } else {
            goToStep(step + 1);
        }
    }

    /** Guarda notas y prioridades de la semana planificada. */
    public void save() {
        LocalDate week = planned;
        String notes = notesArea.getText();
        List<Long> ids = selectedPriorityIds();
        FxAsync.run(() -> context.reviews().save(week, notes, ids), saved -> {
            dirty = false;
            if (AppUi.isInstalled()) {
                AppUi.notifications().success("Revisión guardada: " + Formats.weekTitle(week).toLowerCase()
                        + " con " + ids.size() + (ids.size() == 1 ? " prioridad." : " prioridades."));
            }
            load();
        });
    }

    private void openWeek() {
        if (!AppUi.isInstalled()) {
            return;
        }
        AppUi.navigator().navigate(View.SEMANA);
        if (AppUi.navigator().controller(View.SEMANA) instanceof WeekController week) {
            week.showWeek(planned);
        }
    }

    // ---------------------------------------------------------------- auxiliares

    private List<Long> selectedPriorityIds() {
        List<Long> ids = new ArrayList<>();
        for (ComboBox<Choice<Task>> combo : priorityCombos) {
            Task task = combo.getValue() == null ? null : combo.getValue().value();
            if (task != null) {
                ids.add(task.getId());
            }
        }
        return ids;
    }

    private void selectWeekChoice() {
        weekGroup.getToggles().forEach(t -> t.setSelected(planned.equals(((ToggleButton) t).getUserData())));
    }

    private Button actionButton(String text, String icon,
                                Function<List<Long>, Callable<?>> work) {
        Button button = new Button(text, new FontIcon(icon));
        button.getStyleClass().add("small");
        button.disableProperty().bind(Bindings.isEmpty(
                pendingList.getSelectionModel().getSelectedItems()));
        button.setOnAction(e -> {
            List<Long> ids = pendingList.getSelectionModel().getSelectedItems().stream().map(Task::getId).toList();
            run(work.apply(ids));
        });
        return button;
    }

    private Object setStatus(List<Long> ids, TaskStatus status) {
        for (Long id : ids) {
            context.tasks().setStatus(id, status);
        }
        return null;
    }

    private void run(Callable<?> work) {
        FxAsync.run(work, ignored -> load(), error -> {
            Dialogs.error(error);
            load();
        });
    }

    private static String describe(Task task, LocalDate today) {
        String day = task.getScheduledDate() == null ? "sin fecha" : Formats.relativeDay(task.getScheduledDate(), today);
        return task.getTitle() + "  ·  " + day + (task.isOpen() ? "" : " · " + task.getStatus().getDisplayName().toLowerCase());
    }

    private static VBox panel(String title, ListView<?> list) {
        Label label = new Label(title);
        label.getStyleClass().add("title-4");
        VBox.setVgrow(list, Priority.ALWAYS);
        VBox box = new VBox(8, label, list);
        box.getStyleClass().add("panel");
        return box;
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

    private static Label kpiDetail(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("text-muted");
        return label;
    }

    private static Label placeholder(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("placeholder");
        label.setWrapText(true);
        return label;
    }

    /** Una revisión del historial: semana, foto de la anterior y primera línea de las notas. */
    private final class HistoryCell extends ListCell<WeeklyReview> {
        private final Label week = new Label();
        private final Label counts = new Label();
        private final Label notes = new Label();
        private final VBox root = new VBox(2, week, counts, notes);

        private HistoryCell() {
            getStyleClass().add("history-cell");
            week.getStyleClass().add("history-week");
            week.setWrapText(true);
            counts.getStyleClass().addAll("text-muted", "text-small");
            notes.getStyleClass().addAll("text-muted", "text-small");
            notes.setWrapText(true);
            notes.setMaxHeight(34);
            root.setMinWidth(0);
            setPrefWidth(0);
        }

        @Override
        protected void updateItem(WeeklyReview review, boolean empty) {
            super.updateItem(review, empty);
            setText(null);
            if (empty || review == null) {
                setGraphic(null);
                return;
            }
            week.setText(Formats.weekTitle(review.getWeekStart()));
            counts.setText("Antes: " + review.getCompletedCount() + " hechas · " + review.getPendingCount()
                    + " pendientes");
            String text = review.getNotes() == null ? "" : review.getNotes().lines().findFirst().orElse("");
            notes.setText(text);
            notes.setVisible(!text.isEmpty());
            notes.setManaged(!text.isEmpty());
            root.pseudoClassStateChanged(CURRENT, Objects.equals(review.getWeekStart(), planned));
            setGraphic(root);
        }
    }
}
