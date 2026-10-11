package com.alejandro.synclife.controller;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.model.entity.Goal;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.GoalStatus;
import com.alejandro.synclife.service.GoalService.GoalProgress;
import com.alejandro.synclife.ui.Dialogs;
import com.alejandro.synclife.ui.Formats;
import com.alejandro.synclife.ui.FxAsync;
import com.alejandro.synclife.ui.TaskActions;
import com.alejandro.synclife.ui.Ui;
import com.alejandro.synclife.ui.ViewController;
import com.alejandro.synclife.ui.cell.GoalListCell;
import com.alejandro.synclife.ui.cell.TaskListCell;
import com.alejandro.synclife.ui.control.GoalEditorDialog;
import com.alejandro.synclife.ui.control.LinkTasksDialog;
import com.alejandro.synclife.ui.control.TaskEditorDialog;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pantalla "Objetivos": a la izquierda los objetivos con su avance; a la derecha el elegido, con sus
 * tareas vinculadas y las acciones para crear o vincular tareas.
 */
public class GoalsController implements ViewController {

    private final AppContext context;
    private final ToggleGroup areaGroup = new ToggleGroup();
    private TaskActions actions;
    private List<GoalProgress> all = List.of();
    private Long selectedId;
    private boolean filling;

    @FXML private Label subtitle;
    @FXML private HBox areaFilterBox;
    @FXML private CheckBox showClosed;
    @FXML private ListView<GoalProgress> goalList;
    @FXML private Label emptyDetail;
    @FXML private VBox detail;
    @FXML private Label goalTitle;
    @FXML private Label goalStatus;
    @FXML private HBox goalMeta;
    @FXML private Label goalDescription;
    @FXML private ProgressBar goalProgress;
    @FXML private Label goalProgressText;
    @FXML private Button achieveButton;
    @FXML private ListView<Task> taskList;

    public GoalsController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        actions = new TaskActions(context, goalList, this::onShow).hidingGoal();
        buildAreaFilter();
        goalList.setCellFactory(list -> new GoalListCell(context::today));
        goalList.setPlaceholder(placeholder("Aún no hay objetivos.\nCrea el primero con «Nuevo objetivo»."));
        goalList.getSelectionModel().selectedItemProperty().addListener((obs, old, item) -> {
            if (!filling) {
                selectedId = item == null ? null : item.goal().getId();
                showDetail(item);
            }
        });
        taskList.setCellFactory(list -> new TaskListCell(actions, false));
        taskList.setPlaceholder(placeholder("Sin tareas vinculadas.\nAñade una nueva o vincula tareas que ya tengas."));
        actions.installDeleteKey(taskList);
        showClosed.selectedProperty().addListener((obs, old, value) -> onShow());
        showDetail(null);
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
            } else {
                applyList();
            }
        });
    }

    // ---------------------------------------------------------------- datos

    @Override
    public void onShow() {
        boolean closed = showClosed.isSelected();
        FxAsync.run(() -> context.goals().findAll(closed), goals -> {
            all = goals;
            applyList();
        });
    }

    private void applyList() {
        Area area = (Area) areaGroup.getSelectedToggle().getUserData();
        List<GoalProgress> visible = all.stream().filter(p -> area == null || p.goal().getArea() == area).toList();
        long open = all.stream().filter(p -> p.goal().isOpen()).count();
        long achieved = all.stream().filter(p -> p.goal().getStatus() == GoalStatus.CONSEGUIDO).count();
        subtitle.setText(open + (open == 1 ? " objetivo en curso" : " objetivos en curso")
                + (showClosed.isSelected() ? " · " + achieved + (achieved == 1 ? " conseguido" : " conseguidos") : "")
                + " · Vincula tareas para ver su avance");
        filling = true;
        try {
            goalList.getItems().setAll(visible);
            GoalProgress selected = visible.stream()
                    .filter(p -> Objects.equals(p.goal().getId(), selectedId)).findFirst()
                    .orElse(visible.isEmpty() ? null : visible.get(0));
            goalList.getSelectionModel().select(selected);
            selectedId = selected == null ? null : selected.goal().getId();
            showDetail(selected);
        } finally {
            filling = false;
        }
    }

    private void showDetail(GoalProgress item) {
        detail.setVisible(item != null);
        emptyDetail.setVisible(item == null);
        if (item == null) {
            taskList.getItems().clear();
            return;
        }
        Goal goal = item.goal();
        goalTitle.setText(goal.getTitle());
        goalStatus.setText(goal.getStatus().getDisplayName());
        goalStatus.getStyleClass().setAll("badge", switch (goal.getStatus()) {
            case EN_CURSO -> "badge-accent";
            case CONSEGUIDO -> "badge-success";
            case ABANDONADO -> "badge-muted";
        });
        Label when = new Label(goal.getTargetDate() == null && goal.isOpen() ? "Sin fecha objetivo"
                : goalWhen(goal), new FontIcon("mdi2c-calendar-blank-outline"));
        when.getStyleClass().add(goal.isOverdue(context.today()) ? "goal-overdue" : "text-muted");
        goalMeta.getChildren().setAll(Ui.areaChip(goal.getArea()), when);
        goalDescription.setText(goal.getDescription());
        goalDescription.setVisible(goal.getDescription() != null);
        goalDescription.setManaged(goal.getDescription() != null);
        goalProgress.setProgress(item.rate());
        goalProgress.setStyle("-color-progress-bar-fill: " + goal.getArea().getColorHex() + ";");
        goalProgressText.setText(item.total() == 0 ? "Sin tareas"
                : item.done() + " de " + item.total() + " tareas · " + Math.round(item.rate() * 100) + " %");
        boolean achieved = goal.getStatus() == GoalStatus.CONSEGUIDO;
        achieveButton.setText(achieved ? "Volver a en curso" : "Marcar como conseguido");
        achieveButton.setGraphic(new FontIcon(achieved ? "mdi2r-refresh" : "mdi2t-trophy-outline"));

        long goalId = goal.getId();
        FxAsync.run(() -> context.goals().tasksOf(goalId), tasks -> {
            if (Objects.equals(selectedId, goalId)) {
                taskList.getItems().setAll(tasks);
            }
        });
    }

    private String goalWhen(Goal goal) {
        String date = goal.getTargetDate() == null ? "" : "Fecha objetivo: " + Formats.date(goal.getTargetDate());
        if (!goal.isOpen()) {
            return goal.getCompletedAt() != null
                    ? "Conseguido el " + Formats.date(goal.getCompletedAt().toLocalDate())
                    : goal.getStatus().getDisplayName() + (date.isEmpty() ? "" : " · " + date);
        }
        return date + " · " + Formats.daysLeft(goal.getTargetDate(), context.today());
    }

    private Goal selectedGoal() {
        GoalProgress item = goalList.getSelectionModel().getSelectedItem();
        return item == null ? null : item.goal();
    }

    // ---------------------------------------------------------------- acciones

    @FXML
    private void newGoal() {
        Area area = (Area) areaGroup.getSelectedToggle().getUserData();
        GoalEditorDialog.create(window(), null, area).showAndWait().ifPresent(this::save);
    }

    @FXML
    private void editGoal() {
        Goal goal = selectedGoal();
        if (goal != null) {
            GoalEditorDialog.create(window(), goal, goal.getArea()).showAndWait().ifPresent(this::save);
        }
    }

    private void save(Goal goal) {
        FxAsync.run(() -> context.goals().save(goal), saved -> {
            selectedId = saved.getId();
            onShow();
        }, error -> {
            Dialogs.error(error);
            onShow();
        });
    }

    @FXML
    private void toggleAchieved() {
        Goal goal = selectedGoal();
        if (goal == null) {
            return;
        }
        GoalStatus next = goal.getStatus() == GoalStatus.CONSEGUIDO ? GoalStatus.EN_CURSO : GoalStatus.CONSEGUIDO;
        if (next == GoalStatus.CONSEGUIDO && !showClosed.isSelected()) {
            showClosed.setSelected(true); // para que siga a la vista tras conseguirlo
        }
        FxAsync.run(() -> context.goals().setStatus(goal.getId(), next), saved -> onShow());
    }

    @FXML
    private void deleteGoal() {
        Goal goal = selectedGoal();
        if (goal != null && Dialogs.confirm("¿Eliminar el objetivo «" + goal.getTitle() + "»?",
                "Sus tareas se conservan, sin objetivo.", "Eliminar")) {
            FxAsync.runVoid(() -> context.goals().delete(goal.getId()), () -> {
                selectedId = null;
                onShow();
            });
        }
    }

    @FXML
    private void newTaskForGoal() {
        Goal goal = selectedGoal();
        if (goal != null) {
            TaskEditorDialog.open(window(), context, null, null, goal, this::onShow);
        }
    }

    @FXML
    private void linkTasks() {
        Goal goal = selectedGoal();
        if (goal == null) {
            return;
        }
        FxAsync.run(() -> context.goals().linkable(), candidates ->
                LinkTasksDialog.create(window(), goal.getTitle(), candidates, context.today()).showAndWait()
                        .ifPresent(ids -> FxAsync.run(() -> context.goals().link(goal.getId(), ids), linked -> onShow())));
    }

    private Window window() {
        return goalList.getScene() == null ? null : goalList.getScene().getWindow();
    }

    private static Label placeholder(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("placeholder");
        label.setWrapText(true);
        return label;
    }
}
