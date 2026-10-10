package com.alejandro.synclife.ui.control;

import com.alejandro.synclife.model.DaysOfWeek;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.EnumMap;
import java.util.Map;

/** Siete botones L M X J V S D sincronizados con una máscara de días (ver {@link DaysOfWeek}). */
public class DaysOfWeekPicker extends HBox {

    private final IntegerProperty mask = new SimpleIntegerProperty(this, "mask", DaysOfWeek.WEEKDAYS);
    private final Map<DayOfWeek, ToggleButton> buttons = new EnumMap<>(DayOfWeek.class);
    private boolean updating;

    public DaysOfWeekPicker() {
        super(4);
        getStyleClass().add("days-picker");
        for (DayOfWeek day : DayOfWeek.values()) {
            ToggleButton button = new ToggleButton(DaysOfWeek.letter(day));
            button.getStyleClass().add("day-toggle");
            button.setTooltip(new Tooltip(day.getDisplayName(TextStyle.FULL, com.alejandro.synclife.ui.Formats.ES)));
            button.selectedProperty().addListener((obs, was, selected) -> {
                if (!updating) {
                    int bit = DaysOfWeek.bit(day);
                    mask.set(selected ? mask.get() | bit : mask.get() & ~bit);
                }
            });
            buttons.put(day, button);
            getChildren().add(button);
        }
        mask.addListener((obs, old, value) -> syncButtons(value.intValue()));
        syncButtons(mask.get());
    }

    private void syncButtons(int value) {
        updating = true;
        try {
            buttons.forEach((day, button) -> button.setSelected(DaysOfWeek.contains(value, day)));
        } finally {
            updating = false;
        }
    }

    public int getMask() {
        return mask.get();
    }

    public void setMask(int value) {
        mask.set(value);
    }

    public IntegerProperty maskProperty() {
        return mask;
    }
}
