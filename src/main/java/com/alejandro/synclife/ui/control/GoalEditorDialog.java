package com.alejandro.synclife.ui.control;

import com.alejandro.synclife.model.entity.Goal;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.GoalStatus;
import com.alejandro.synclife.ui.Theme;
import com.alejandro.synclife.ui.Ui;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Window;

/** Diálogo para crear o editar un objetivo (título, descripción, área, fecha objetivo y estado). */
public final class GoalEditorDialog {

    private GoalEditorDialog() {
    }

    /** {@code goal == null} crea uno nuevo en {@code defaultArea}. Devuelve el objetivo con los cambios aplicados. */
    public static Dialog<Goal> create(Window owner, Goal goal, Area defaultArea) {
        TextField title = new TextField();
        title.setPromptText("p. ej. Correr una 10K, Lanzar la versión 2.0…");
        Ui.limitLength(title, 120);
        TextArea description = new TextArea();
        description.setPromptText("Por qué es importante, cómo sabrás que lo has conseguido…");
        description.setPrefRowCount(3);
        description.setWrapText(true);
        ComboBox<Area> area = new ComboBox<>();
        area.getItems().setAll(Area.values());
        area.setPrefWidth(160);
        DatePicker target = new DatePicker();
        target.setPromptText("Sin fecha");
        target.setPrefWidth(180);
        ComboBox<GoalStatus> status = new ComboBox<>();
        status.getItems().setAll(GoalStatus.values());
        status.setPrefWidth(160);

        if (goal == null) {
            area.setValue(defaultArea == null ? Area.TRABAJO : defaultArea);
            status.setValue(GoalStatus.EN_CURSO);
        } else {
            title.setText(goal.getTitle());
            description.setText(goal.getDescription());
            area.setValue(goal.getArea());
            target.setValue(goal.getTargetDate());
            status.setValue(goal.getStatus());
        }

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(4, 0, 0, 0));
        ColumnConstraints labels = new ColumnConstraints(120);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, fields);
        grid.addRow(0, new Label("Título *"), title);
        Label descriptionLabel = new Label("Descripción");
        GridPane.setValignment(descriptionLabel, VPos.TOP);
        grid.addRow(1, descriptionLabel, description);
        grid.addRow(2, new Label("Área"), area);
        grid.addRow(3, new Label("Fecha objetivo"), target);
        grid.addRow(4, new Label("Estado"), status);
        grid.setPrefWidth(540);

        Dialog<Goal> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle(goal == null ? "Nuevo objetivo" : "Editar objetivo");
        dialog.setHeaderText(goal == null ? "Nuevo objetivo" : "Editar objetivo");
        ButtonType save = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().lookupButton(save).disableProperty().bind(Bindings.createBooleanBinding(
                () -> title.getText() == null || title.getText().isBlank(), title.textProperty()));
        Theme.style(dialog.getDialogPane());
        dialog.setResultConverter(button -> {
            if (button != save) {
                return null;
            }
            Goal result = goal == null ? new Goal() : goal;
            result.setTitle(title.getText().trim());
            String text = description.getText();
            result.setDescription(text == null || text.isBlank() ? null : text.trim());
            result.setArea(area.getValue());
            result.setTargetDate(Ui.datePickerValue(target));
            result.setStatus(status.getValue());
            return result;
        });
        dialog.setOnShown(e -> Platform.runLater(title::requestFocus));
        return dialog;
    }
}
