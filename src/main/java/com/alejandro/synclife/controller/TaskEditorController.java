package com.alejandro.synclife.controller;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.model.ChecklistItem;
import com.alejandro.synclife.model.entity.Category;
import com.alejandro.synclife.model.entity.Goal;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.entity.TimeEntry;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.Priority;
import com.alejandro.synclife.model.enums.Quadrant;
import com.alejandro.synclife.model.enums.TaskStatus;
import com.alejandro.synclife.model.enums.TimeSource;
import com.alejandro.synclife.service.TimeTrackingService;
import com.alejandro.synclife.ui.AppUi;
import com.alejandro.synclife.ui.Choice;
import com.alejandro.synclife.ui.Dialogs;
import com.alejandro.synclife.ui.Formats;
import com.alejandro.synclife.ui.FxAsync;
import com.alejandro.synclife.ui.Theme;
import com.alejandro.synclife.ui.Ui;
import com.alejandro.synclife.ui.cell.CategoryChoiceCell;
import com.alejandro.synclife.ui.control.ChecklistEditor;
import com.alejandro.synclife.ui.control.TimeField;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Formulario del diálogo de nueva tarea / editar tarea: datos de la tarea, cuadrante de Eisenhower,
 * tiempo real registrado y lista de comprobación.
 *
 * <p>El Pomodoro y los apuntes de tiempo se guardan al momento (como el propio Pomodoro); el resto del
 * formulario, solo al pulsar "Guardar".
 */
public class TaskEditorController {

    private static final DateTimeFormatter ENTRY_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final AppContext context;

    @FXML private Label routineLabel;
    @FXML private TextField titleField;
    @FXML private TextArea descriptionArea;
    @FXML private ComboBox<Area> areaCombo;
    @FXML private ComboBox<Choice<Category>> categoryCombo;
    @FXML private ComboBox<Priority> priorityCombo;
    @FXML private ComboBox<TaskStatus> statusCombo;
    @FXML private Label quadrantLabel;
    @FXML private HBox quadrantRow;
    @FXML private ComboBox<Choice<Quadrant>> quadrantCombo;
    @FXML private Label goalLabel;
    @FXML private HBox goalRow;
    @FXML private ComboBox<Choice<Goal>> goalCombo;
    @FXML private DatePicker scheduledPicker;
    @FXML private TimeField timeField;
    @FXML private Spinner<Integer> estimateSpinner;
    @FXML private DatePicker duePicker;
    @FXML private Label timeLabel;
    @FXML private HBox timeRow;
    @FXML private Label trackedLabel;
    @FXML private Button pomodoroButton;
    @FXML private MenuButton timeMenu;
    @FXML private Node checklistBox;
    @FXML private Label checklistTitle;
    @FXML private ChecklistEditor checklistEditor;

    private List<Category> categories = List.of();
    private List<TimeEntry> entries = List.of();
    private Task editing;
    private BooleanBinding invalid;

    public TaskEditorController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        Ui.limitLength(titleField, 200);
        areaCombo.getItems().setAll(Area.values());
        priorityCombo.getItems().setAll(Priority.values());
        statusCombo.getItems().setAll(TaskStatus.values());
        estimateSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 720, 0, 15));
        categoryCombo.setCellFactory(list -> new CategoryChoiceCell());
        categoryCombo.setButtonCell(new CategoryChoiceCell());

        quadrantCombo.getItems().add(Choice.of("Sin clasificar", null));
        for (Quadrant quadrant : Quadrant.values()) {
            quadrantCombo.getItems().add(Choice.of(
                    quadrant.getDisplayName() + " · " + quadrant.getDescription().toLowerCase(), quadrant));
        }
        quadrantCombo.setCellFactory(list -> new QuadrantCell());
        quadrantCombo.setButtonCell(new QuadrantCell());
        quadrantCombo.getSelectionModel().selectFirst();

        areaCombo.valueProperty().addListener((obs, old, area) -> refreshCategories(area));
        checklistTitle.textProperty().bind(Bindings.createStringBinding(() -> {
            String summary = checklistEditor.summaryProperty().get();
            return summary.isEmpty() ? "Lista de comprobación" : "Lista de comprobación (" + summary + ")";
        }, checklistEditor.summaryProperty()));

        invalid = Bindings.createBooleanBinding(
                () -> titleField.getText() == null || titleField.getText().isBlank() || !timeField.isValid(),
                titleField.textProperty(), timeField.validProperty());
        showRow(timeLabel, timeRow, false);
    }

    public void setCategories(List<Category> categories) {
        this.categories = categories;
        refreshCategories(areaCombo.getValue());
    }

    /** Objetivos en curso que se pueden elegir (más el de la tarea, aunque ya esté cerrado). */
    public void setGoals(List<Goal> goals, Goal current) {
        goalCombo.getItems().setAll(Choice.of("Sin objetivo", null));
        for (Goal goal : goals) {
            goalCombo.getItems().add(Choice.of(goal.getTitle(), goal));
        }
        if (current != null && !goals.contains(current)) {
            goalCombo.getItems().add(Choice.of(current.getTitle() + " (" + current.getStatus().getDisplayName().toLowerCase()
                    + ")", current));
        }
        Ui.selectChoice(goalCombo, current);
    }

    /** Rellena el formulario. {@code task == null} significa "nueva tarea" en {@code defaultDate}. */
    public void edit(Task task, LocalDate defaultDate, List<ChecklistItem> checklist, List<TimeEntry> timeEntries) {
        editing = task;
        if (task == null) {
            areaCombo.setValue(Area.TRABAJO);
            priorityCombo.setValue(Priority.MEDIA);
            statusCombo.setValue(TaskStatus.PENDIENTE);
            scheduledPicker.setValue(defaultDate);
            checklistEditor.setItems(List.of());
            return;
        }
        if (task.getCategory() != null && !categories.contains(task.getCategory())) {
            // Categoría archivada: se ofrece igualmente para no perderla al guardar
            List<Category> extended = new ArrayList<>(categories);
            extended.add(task.getCategory());
            categories = extended;
        }
        titleField.setText(task.getTitle());
        descriptionArea.setText(task.getDescription());
        areaCombo.setValue(task.getArea());
        refreshCategories(task.getArea());
        Ui.selectChoice(categoryCombo, task.getCategory());
        priorityCombo.setValue(task.getPriority());
        statusCombo.setValue(task.getStatus());
        Ui.selectChoice(quadrantCombo, task.getQuadrant());
        scheduledPicker.setValue(task.getScheduledDate());
        timeField.setTime(task.getStartTime());
        estimateSpinner.getValueFactory().setValue(
                task.getEstimatedMinutes() == null ? 0 : task.getEstimatedMinutes());
        duePicker.setValue(task.getDueDate());
        checklistEditor.setItems(checklist);

        showRow(timeLabel, timeRow, true);
        setEntries(timeEntries);
        updatePomodoroButton();

        if (task.isRoutineInstance()) {
            scheduledPicker.setDisable(true);
            routineLabel.setText("Generada por la rutina «" + task.getRoutine().getName()
                    + "». Para cambiar el día o repetirla, edita la rutina.");
            routineLabel.setVisible(true);
            routineLabel.setManaged(true);
            // Se regeneran al editar la rutina: no llevan lista de comprobación, cuadrante ni objetivo
            showRow(quadrantLabel, quadrantRow, false);
            showRow(goalLabel, goalRow, false);
            checklistBox.setVisible(false);
            checklistBox.setManaged(false);
        }
    }

    /** Vuelca el formulario en {@code target} (solo se llama al pulsar Guardar). */
    public Task applyTo(Task target) {
        target.setTitle(titleField.getText().trim());
        String description = descriptionArea.getText();
        target.setDescription(description == null || description.isBlank() ? null : description.trim());
        target.setArea(areaCombo.getValue());
        target.setCategory(Ui.choiceValue(categoryCombo));
        target.setPriority(priorityCombo.getValue());
        target.setStatus(statusCombo.getValue());
        if (!target.isRoutineInstance()) {
            target.setScheduledDate(Ui.datePickerValue(scheduledPicker));
            target.setQuadrant(Ui.choiceValue(quadrantCombo));
            target.setGoal(Ui.choiceValue(goalCombo));
        }
        target.setStartTime(timeField.getTime());
        Integer minutes = spinnerValue();
        target.setEstimatedMinutes(minutes == null || minutes == 0 ? null : minutes);
        target.setDueDate(Ui.datePickerValue(duePicker));
        return target;
    }

    /** Para una tarea nueva: empieza en {@code area} (p. ej. la del objetivo al que se añade). */
    public void useArea(Area area) {
        areaCombo.setValue(area);
    }

    /** La lista de comprobación tal como queda en el formulario (vacía para las tareas de rutina). */
    public List<ChecklistItem> checklist() {
        return editing != null && editing.isRoutineInstance() ? List.of() : checklistEditor.getItems();
    }

    public ChecklistEditor checklistEditor() {
        return checklistEditor;
    }

    public BooleanBinding invalidProperty() {
        return invalid;
    }

    public void focusTitle() {
        titleField.requestFocus();
        titleField.end();
    }

    // ---------------------------------------------------------------- tiempo real

    @FXML
    private void startPomodoro() {
        if (editing == null || !AppUi.isInstalled()) {
            return;
        }
        AppUi.pomodoro().start(editing);
        // El Pomodoro la pone "En progreso": que "Guardar" no lo deshaga
        if (statusCombo.getValue() == TaskStatus.PENDIENTE) {
            statusCombo.setValue(TaskStatus.EN_PROGRESO);
        }
        pomodoroButton.setDisable(true);
    }

    private void addManualTime() {
        TextInputDialog dialog = new TextInputDialog("25");
        dialog.initOwner(trackedLabel.getScene().getWindow());
        dialog.setTitle("Apuntar tiempo");
        dialog.setHeaderText("¿Cuántos minutos has dedicado a «" + editing.getTitle() + "»?");
        dialog.setContentText("Minutos:");
        Theme.style(dialog.getDialogPane());
        dialog.showAndWait().ifPresent(text -> {
            int minutes;
            try {
                minutes = Integer.parseInt(text.trim());
            } catch (NumberFormatException e) {
                Dialogs.info("Revisa los datos", "Escribe un número de minutos, por ejemplo 30.");
                return;
            }
            long taskId = editing.getId();
            FxAsync.run(() -> {
                context.timeTracking().addManual(taskId, minutes);
                return context.timeTracking().entries(taskId);
            }, this::setEntries);
        });
    }

    private void deleteEntry(TimeEntry entry) {
        if (!Dialogs.confirm("¿Borrar este registro?",
                Formats.duration(entry.getMinutes()) + " del " + describe(entry) + ".", "Borrar")) {
            return;
        }
        long taskId = editing.getId();
        FxAsync.run(() -> {
            context.timeTracking().deleteEntry(entry.getId());
            return context.timeTracking().entries(taskId);
        }, this::setEntries);
    }

    private void setEntries(List<TimeEntry> timeEntries) {
        entries = timeEntries;
        int tracked = entries.stream().mapToInt(TimeEntry::getMinutes).sum();
        Integer estimate = spinnerValueOrNull();
        trackedLabel.setText(tracked == 0 ? "Sin tiempo registrado" : Formats.trackedVsEstimate(tracked, estimate));
        trackedLabel.getStyleClass().remove("over");
        if (estimate != null && estimate > 0 && tracked > estimate * 1.1) {
            trackedLabel.getStyleClass().add("over");
        }

        timeMenu.getItems().clear();
        MenuItem manual = new MenuItem("Apuntar tiempo…", new FontIcon("mdi2c-clock-plus-outline"));
        manual.setOnAction(e -> addManualTime());
        timeMenu.getItems().add(manual);
        if (!entries.isEmpty()) {
            timeMenu.getItems().add(new SeparatorMenuItem());
            MenuItem header = new MenuItem("Pulsa un registro para borrarlo");
            header.setDisable(true);
            timeMenu.getItems().add(header);
            for (TimeEntry entry : entries.subList(0, Math.min(entries.size(), 15))) {
                MenuItem item = new MenuItem(describe(entry) + " · " + Formats.duration(entry.getMinutes())
                        + " · " + entry.getSource().getDisplayName(),
                        new FontIcon(entry.getSource() == TimeSource.POMODORO
                                ? "mdi2t-timer-outline" : "mdi2p-pencil-outline"));
                item.setOnAction(e -> deleteEntry(entry));
                timeMenu.getItems().add(item);
            }
        }
        timeMenu.setTooltip(new Tooltip(entries.isEmpty() ? "Apuntar tiempo a mano"
                : entries.size() + (entries.size() == 1 ? " registro" : " registros")
                + " · máx. " + TimeTrackingService.MAX_MINUTES / 60 + " h por registro"));
    }

    private String describe(TimeEntry entry) {
        return Formats.relativeDay(entry.getStartedAt().toLocalDate(), context.today()) + " "
                + ENTRY_TIME.format(entry.getStartedAt());
    }

    private void updatePomodoroButton() {
        boolean available = editing != null && editing.isOpen() && AppUi.isInstalled()
                && !AppUi.pomodoro().isWorkingOn(editing);
        pomodoroButton.setDisable(!available);
        pomodoroButton.setTooltip(new Tooltip(editing != null && !editing.isOpen()
                ? "La tarea está cerrada" : "Empieza un pomodoro con esta tarea"));
    }

    // ---------------------------------------------------------------- auxiliares

    private static void showRow(Node label, Node row, boolean visible) {
        label.setVisible(visible);
        label.setManaged(visible);
        row.setVisible(visible);
        row.setManaged(visible);
    }

    private Integer spinnerValueOrNull() {
        Integer value = estimateSpinner.getValue();
        return value == null || value == 0 ? null : value;
    }

    private Integer spinnerValue() {
        // Confirma lo tecleado aunque no se haya pulsado Enter
        String text = estimateSpinner.getEditor().getText();
        try {
            if (text != null && !text.isBlank()) {
                estimateSpinner.getValueFactory().setValue(Integer.parseInt(text.trim()));
            }
        } catch (NumberFormatException ignored) {
            // se queda con el último valor válido
        }
        return estimateSpinner.getValue();
    }

    private void refreshCategories(Area area) {
        Category selected = Ui.choiceValue(categoryCombo);
        categoryCombo.getItems().setAll(Choice.of("Sin categoría", null));
        for (Category category : categories) {
            if (category.getArea() == area) {
                categoryCombo.getItems().add(Choice.of(category.getName(), category));
            }
        }
        Ui.selectChoice(categoryCombo, selected != null && selected.getArea() == area ? selected : null);
    }

    /** Opción del selector de cuadrante, con su icono y color. */
    private static final class QuadrantCell extends ListCell<Choice<Quadrant>> {
        @Override
        protected void updateItem(Choice<Quadrant> choice, boolean empty) {
            super.updateItem(choice, empty);
            if (empty || choice == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(choice.label());
            Quadrant quadrant = choice.value();
            FontIcon icon = new FontIcon(quadrant == null ? "mdi2h-help-circle-outline" : quadrant.getIconLiteral());
            if (quadrant != null) {
                icon.setIconColor(Color.web(quadrant.getColorHex()));
            }
            setGraphic(icon);
        }
    }
}
