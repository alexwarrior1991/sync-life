package com.alejandro.synclife.ui;

import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.Priority;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/** Pequeñas piezas visuales reutilizadas por varias pantallas. */
public final class Ui {

    private Ui() {
    }

    public static FontIcon icon(String literal) {
        return new FontIcon(literal);
    }

    public static Circle dot(String hexColor, double radius) {
        Circle circle = new Circle(radius, Color.web(hexColor));
        circle.getStyleClass().add("color-dot");
        return circle;
    }

    /** Color con el que se pinta una tarea: el de su categoría o, si no tiene, el de su área. */
    public static String colorOf(Task task) {
        return task.getCategory() != null ? task.getCategory().getColorHex() : task.getArea().getColorHex();
    }

    public static Label areaChip(Area area) {
        Label chip = new Label(area.getDisplayName(), dot(area.getColorHex(), 4));
        chip.getStyleClass().addAll("chip", "chip-" + area.name().toLowerCase(Locale.ROOT));
        return chip;
    }

    public static Label priorityBadge(Priority priority) {
        Label badge = new Label(priority.getDisplayName());
        badge.getStyleClass().addAll("badge", "priority-" + priority.name().toLowerCase(Locale.ROOT));
        return badge;
    }

    public static Region hSpacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        return spacer;
    }

    public static <T> void selectChoice(ComboBox<Choice<T>> combo, T value) {
        for (Choice<T> choice : combo.getItems()) {
            if (Objects.equals(choice.value(), value)) {
                combo.getSelectionModel().select(choice);
                return;
            }
        }
        combo.getSelectionModel().selectFirst();
    }

    public static <T> T choiceValue(ComboBox<Choice<T>> combo) {
        Choice<T> choice = combo.getValue();
        return choice == null ? null : choice.value();
    }

    /**
     * Valor de un DatePicker teniendo en cuenta lo tecleado y aún no confirmado con Enter
     * (el DatePicker solo actualiza su valor al pulsar Enter o perder el foco).
     */
    public static LocalDate datePickerValue(DatePicker picker) {
        String text = picker.getEditor().getText();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            LocalDate typed = picker.getConverter().fromString(text.trim());
            if (typed != null) {
                picker.setValue(typed);
            }
            return typed;
        } catch (RuntimeException e) {
            return picker.getValue();
        }
    }

    /** Impide escribir más de {@code max} caracteres (los mismos límites que valida el servicio). */
    public static void limitLength(TextInputControl field, int max) {
        field.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= max ? change : null));
    }

    public static String toHex(Color color) {
        return String.format("#%02X%02X%02X",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }
}
