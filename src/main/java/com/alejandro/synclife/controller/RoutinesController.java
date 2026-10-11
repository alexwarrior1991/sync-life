package com.alejandro.synclife.controller;

import atlantafx.base.controls.ToggleSwitch;
import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.model.DaysOfWeek;
import com.alejandro.synclife.model.entity.Category;
import com.alejandro.synclife.model.entity.Routine;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.Priority;
import com.alejandro.synclife.service.RoutineScheduler;
import com.alejandro.synclife.ui.Choice;
import com.alejandro.synclife.ui.Dialogs;
import com.alejandro.synclife.ui.Formats;
import com.alejandro.synclife.ui.FxAsync;
import com.alejandro.synclife.ui.ReminderOptions;
import com.alejandro.synclife.ui.Ui;
import com.alejandro.synclife.ui.ViewController;
import com.alejandro.synclife.ui.cell.CategoryChoiceCell;
import com.alejandro.synclife.ui.cell.RoutineListCell;
import com.alejandro.synclife.ui.control.DaysOfWeekPicker;
import com.alejandro.synclife.ui.control.TimeField;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Pantalla "Rutinas": lista de rutinas a la izquierda y formulario de edición a la derecha. */
public class RoutinesController implements ViewController {

    private record Data(List<Routine> routines, List<Category> categories) {
    }

    private final AppContext context;
    private List<Category> categories = List.of();
    /** Rutina que se está editando (null = nueva). */
    private Routine editing;
    private Long selectedId;
    private boolean filling;

    @FXML private ListView<Routine> routineList;
    @FXML private Label formTitle;
    @FXML private TextField nameField;
    @FXML private TextArea descriptionArea;
    @FXML private ComboBox<Area> areaCombo;
    @FXML private ComboBox<Choice<Category>> categoryCombo;
    @FXML private DaysOfWeekPicker daysPicker;
    @FXML private TimeField timeField;
    @FXML private Spinner<Integer> durationSpinner;
    @FXML private ComboBox<Priority> priorityCombo;
    @FXML private DatePicker startPicker;
    @FXML private DatePicker endPicker;
    @FXML private ComboBox<Choice<Integer>> remindCombo;
    @FXML private ToggleSwitch activeSwitch;
    @FXML private Label previewLabel;
    @FXML private Button saveButton;
    @FXML private Button deleteButton;

    public RoutinesController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        Ui.limitLength(nameField, 120);
        areaCombo.getItems().setAll(Area.values());
        priorityCombo.getItems().setAll(Priority.values());
        remindCombo.getItems().setAll(ReminderOptions.forRoutines());
        durationSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 480, 0, 5));
        categoryCombo.setCellFactory(list -> new CategoryChoiceCell());
        categoryCombo.setButtonCell(new CategoryChoiceCell());
        areaCombo.valueProperty().addListener((obs, old, area) -> refreshCategories(area));

        routineList.setCellFactory(list -> new RoutineListCell(this::setActive));
        Label empty = new Label("Aún no hay rutinas.\nCrea la primera con «Nueva rutina».");
        empty.getStyleClass().add("placeholder");
        routineList.setPlaceholder(empty);
        routineList.getSelectionModel().selectedItemProperty().addListener((obs, old, routine) -> {
            if (routine != null && !filling) {
                edit(routine);
            }
        });

        saveButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> nameField.getText() == null || nameField.getText().isBlank()
                        || daysPicker.getMask() == 0 || !timeField.isValid() || startPicker.getValue() == null,
                nameField.textProperty(), daysPicker.maskProperty(), timeField.validProperty(),
                startPicker.valueProperty()));

        for (Observable source : List.<Observable>of(daysPicker.maskProperty(), startPicker.valueProperty(),
                endPicker.valueProperty(), activeSwitch.selectedProperty())) {
            source.addListener(o -> updatePreview());
        }
        clearForm();
    }

    @Override
    public void onShow() {
        FxAsync.run(() -> new Data(context.routines().findAll(), context.categories().findAll(false)), data -> {
            categories = data.categories();
            filling = true;
            try {
                routineList.getItems().setAll(data.routines());
                Routine selected = data.routines().stream()
                        .filter(r -> Objects.equals(r.getId(), selectedId)).findFirst().orElse(null);
                if (selected != null) {
                    routineList.getSelectionModel().select(selected);
                    edit(selected);
                } else {
                    routineList.getSelectionModel().clearSelection();
                    clearForm();
                }
            } finally {
                filling = false;
            }
        });
    }

    // ---------------------------------------------------------------- formulario

    private void edit(Routine routine) {
        editing = routine;
        selectedId = routine.getId();
        formTitle.setText("Editar rutina");
        nameField.setText(routine.getName());
        descriptionArea.setText(routine.getDescription());
        areaCombo.setValue(routine.getArea());
        refreshCategories(routine.getArea());
        Ui.selectChoice(categoryCombo, routine.getCategory());
        daysPicker.setMask(routine.getDaysMask());
        timeField.setTime(routine.getStartTime());
        durationSpinner.getValueFactory().setValue(
                routine.getDurationMinutes() == null ? 0 : routine.getDurationMinutes());
        priorityCombo.setValue(routine.getPriority());
        startPicker.setValue(routine.getStartDate());
        endPicker.setValue(routine.getEndDate());
        Ui.selectChoice(remindCombo, routine.getRemindMinutesBefore());
        activeSwitch.setSelected(routine.isActive());
        deleteButton.setVisible(true);
        updatePreview();
    }

    private void clearForm() {
        editing = null;
        selectedId = null;
        formTitle.setText("Nueva rutina");
        nameField.clear();
        descriptionArea.clear();
        areaCombo.setValue(Area.TRABAJO);
        refreshCategories(Area.TRABAJO);
        daysPicker.setMask(DaysOfWeek.WEEKDAYS);
        timeField.clear();
        durationSpinner.getValueFactory().setValue(0);
        priorityCombo.setValue(Priority.MEDIA);
        startPicker.setValue(context.today());
        endPicker.setValue(null);
        remindCombo.getSelectionModel().selectFirst();
        activeSwitch.setSelected(true);
        deleteButton.setVisible(false);
        updatePreview();
    }

    /** Copia el formulario en {@code target}. */
    private Routine fill(Routine target) {
        target.setName(nameField.getText() == null ? "" : nameField.getText().trim());
        target.setDescription(descriptionArea.getText());
        target.setArea(areaCombo.getValue());
        target.setCategory(Ui.choiceValue(categoryCombo));
        target.setDaysMask(daysPicker.getMask());
        target.setStartTime(timeField.getTime());
        Integer minutes = durationSpinner.getValue();
        target.setDurationMinutes(minutes == null || minutes == 0 ? null : minutes);
        target.setPriority(priorityCombo.getValue());
        target.setStartDate(Ui.datePickerValue(startPicker));
        target.setEndDate(Ui.datePickerValue(endPicker));
        target.setRemindMinutesBefore(Ui.choiceValue(remindCombo));
        target.setActive(activeSwitch.isSelected());
        return target;
    }

    private void updatePreview() {
        if (daysPicker.getMask() == 0) {
            previewLabel.setText("Elige al menos un día de la semana.");
            return;
        }
        if (startPicker.getValue() == null) {
            previewLabel.setText("Indica desde cuándo empieza.");
            return;
        }
        Routine preview = new Routine("vista previa", Area.TRABAJO, daysPicker.getMask(), startPicker.getValue());
        preview.setEndDate(endPicker.getValue());
        List<LocalDate> next = RoutineScheduler.nextOccurrences(preview, context.today(), 5);
        String days = next.stream().map(d -> Formats.relativeDay(d, context.today()))
                .collect(Collectors.joining(", "));
        String text = next.isEmpty() ? "No hay próximas fechas en el periodo indicado." : "Próximas: " + days;
        previewLabel.setText(activeSwitch.isSelected() ? text : "En pausa — no genera tareas. " + text);
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

    // ---------------------------------------------------------------- acciones

    @FXML
    private void newRoutine() {
        filling = true;
        routineList.getSelectionModel().clearSelection();
        filling = false;
        clearForm();
        nameField.requestFocus();
    }

    @FXML
    private void save() {
        commitSpinner();
        Routine routine = fill(editing != null ? editing : new Routine());
        FxAsync.run(() -> context.routines().save(routine), saved -> {
            selectedId = saved.getId();
            onShow();
        }); // si falla, se muestra el error y el formulario conserva lo escrito
    }

    @FXML
    private void discard() {
        if (editing != null) {
            onShow(); // recarga desde la base de datos
        } else {
            clearForm();
        }
    }

    @FXML
    private void delete() {
        if (editing == null) {
            return;
        }
        Routine routine = editing;
        if (Dialogs.confirm("¿Eliminar la rutina «" + routine.getName() + "»?",
                "Se borrarán sus tareas pendientes de hoy en adelante. Las ya completadas se conservan.",
                "Eliminar")) {
            FxAsync.runVoid(() -> context.routines().delete(routine.getId()), () -> {
                selectedId = null;
                onShow();
            });
        }
    }

    @FXML
    private void presetWeekdays() {
        daysPicker.setMask(DaysOfWeek.WEEKDAYS);
    }

    @FXML
    private void presetWeekend() {
        daysPicker.setMask(DaysOfWeek.WEEKEND);
    }

    @FXML
    private void presetAll() {
        daysPicker.setMask(DaysOfWeek.ALL);
    }

    private void setActive(Routine routine, boolean active) {
        FxAsync.run(() -> context.routines().setActive(routine.getId(), active), saved -> onShow(), error -> {
            Dialogs.error(error);
            onShow();
        });
    }

    private void commitSpinner() {
        String text = durationSpinner.getEditor().getText();
        try {
            if (text != null && !text.isBlank()) {
                durationSpinner.getValueFactory().setValue(Integer.parseInt(text.trim()));
            }
        } catch (NumberFormatException ignored) {
            // se queda con el último valor válido
        }
    }
}
