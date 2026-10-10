package com.alejandro.synclife.ui.cell;

import atlantafx.base.controls.ToggleSwitch;
import com.alejandro.synclife.model.DaysOfWeek;
import com.alejandro.synclife.model.entity.Routine;
import com.alejandro.synclife.ui.Formats;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** Rutina en la lista: icono del área, nombre, resumen ("L X V · 19:00 · 1 h") e interruptor de activa. */
public class RoutineListCell extends ListCell<Routine> {

    private static final PseudoClass INACTIVE = PseudoClass.getPseudoClass("inactive");

    private final FontIcon icon = new FontIcon();
    private final Label name = new Label();
    private final Label summary = new Label();
    private final ToggleSwitch active = new ToggleSwitch();
    private final HBox root;
    private boolean updating;

    public RoutineListCell(BiConsumer<Routine, Boolean> onActiveChanged) {
        getStyleClass().add("routine-cell");
        name.getStyleClass().add("routine-name");
        summary.getStyleClass().addAll("text-muted", "text-small");
        VBox text = new VBox(2, name, summary);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        root = new HBox(10, icon, text, active);
        root.setAlignment(Pos.CENTER_LEFT);
        active.setTooltip(new Tooltip("Activar o pausar la rutina"));
        active.selectedProperty().addListener((obs, was, selected) -> {
            if (!updating && getItem() != null && getItem().isActive() != selected) {
                onActiveChanged.accept(getItem(), selected);
            }
        });
    }

    @Override
    protected void updateItem(Routine routine, boolean empty) {
        super.updateItem(routine, empty);
        setText(null);
        if (empty || routine == null) {
            setGraphic(null);
            return;
        }
        icon.setIconLiteral(routine.getArea().getIconLiteral());
        icon.setIconColor(Color.web(routine.getCategory() != null
                ? routine.getCategory().getColorHex() : routine.getArea().getColorHex()));
        name.setText(routine.getName());

        List<String> parts = new ArrayList<>();
        parts.add(DaysOfWeek.format(routine.getDaysMask()));
        if (routine.getStartTime() != null) {
            parts.add(Formats.time(routine.getStartTime()));
        }
        if (routine.getDurationMinutes() != null) {
            parts.add(Formats.duration(routine.getDurationMinutes()));
        }
        if (routine.getCategory() != null) {
            parts.add(routine.getCategory().getName());
        }
        if (routine.getEndDate() != null) {
            parts.add("hasta el " + Formats.date(routine.getEndDate()));
        }
        summary.setText(String.join(" · ", parts));

        updating = true;
        active.setSelected(routine.isActive());
        updating = false;
        root.pseudoClassStateChanged(INACTIVE, !routine.isActive());
        setGraphic(root);
    }
}
