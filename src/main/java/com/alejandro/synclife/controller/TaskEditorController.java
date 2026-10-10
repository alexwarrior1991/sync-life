package com.alejandro.synclife.controller;

import com.alejandro.synclife.model.entity.Category;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.Priority;
import com.alejandro.synclife.model.enums.TaskStatus;
import com.alejandro.synclife.ui.Choice;
import com.alejandro.synclife.ui.Ui;
import com.alejandro.synclife.ui.cell.CategoryChoiceCell;
import com.alejandro.synclife.ui.control.TimeField;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Formulario del diálogo de nueva tarea / editar tarea. */
public class TaskEditorController {

    @FXML private Label routineLabel;
    @FXML private TextField titleField;
    @FXML private TextArea descriptionArea;
    @FXML private ComboBox<Area> areaCombo;
    @FXML private ComboBox<Choice<Category>> categoryCombo;
    @FXML private ComboBox<Priority> priorityCombo;
    @FXML private ComboBox<TaskStatus> statusCombo;
    @FXML private DatePicker scheduledPicker;
    @FXML private TimeField timeField;
    @FXML private Spinner<Integer> estimateSpinner;
    @FXML private DatePicker duePicker;

    private List<Category> categories = List.of();
    private BooleanBinding invalid;

    @FXML
    private void initialize() {
        Ui.limitLength(titleField, 200);
        areaCombo.getItems().setAll(Area.values());
        priorityCombo.getItems().setAll(Priority.values());
        statusCombo.getItems().setAll(TaskStatus.values());
        estimateSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 720, 0, 15));
        categoryCombo.setCellFactory(list -> new CategoryChoiceCell());
        categoryCombo.setButtonCell(new CategoryChoiceCell());

        areaCombo.valueProperty().addListener((obs, old, area) -> refreshCategories(area));

        invalid = Bindings.createBooleanBinding(
                () -> titleField.getText() == null || titleField.getText().isBlank() || !timeField.isValid(),
                titleField.textProperty(), timeField.validProperty());
    }

    public void setCategories(List<Category> categories) {
        this.categories = categories;
        refreshCategories(areaCombo.getValue());
    }

    /** Rellena el formulario. {@code task == null} significa "nueva tarea" en {@code defaultDate}. */
    public void edit(Task task, LocalDate defaultDate) {
        if (task == null) {
            areaCombo.setValue(Area.TRABAJO);
            priorityCombo.setValue(Priority.MEDIA);
            statusCombo.setValue(TaskStatus.PENDIENTE);
            scheduledPicker.setValue(defaultDate);
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
        scheduledPicker.setValue(task.getScheduledDate());
        timeField.setTime(task.getStartTime());
        estimateSpinner.getValueFactory().setValue(
                task.getEstimatedMinutes() == null ? 0 : task.getEstimatedMinutes());
        duePicker.setValue(task.getDueDate());

        if (task.isRoutineInstance()) {
            scheduledPicker.setDisable(true);
            routineLabel.setText("Generada por la rutina «" + task.getRoutine().getName()
                    + "». Para cambiar el día o repetirla, edita la rutina.");
            routineLabel.setVisible(true);
            routineLabel.setManaged(true);
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
        }
        target.setStartTime(timeField.getTime());
        Integer minutes = spinnerValue();
        target.setEstimatedMinutes(minutes == null || minutes == 0 ? null : minutes);
        target.setDueDate(Ui.datePickerValue(duePicker));
        return target;
    }

    public BooleanBinding invalidProperty() {
        return invalid;
    }

    public void focusTitle() {
        titleField.requestFocus();
        titleField.end();
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
}
