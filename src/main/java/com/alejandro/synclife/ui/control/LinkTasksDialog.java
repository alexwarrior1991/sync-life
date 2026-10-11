package com.alejandro.synclife.ui.control;

import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.ui.Formats;
import com.alejandro.synclife.ui.Theme;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Diálogo para elegir (con casillas) qué tareas sin objetivo se vinculan a uno. Devuelve sus ids. */
public final class LinkTasksDialog {

    private LinkTasksDialog() {
    }

    public static Dialog<List<Long>> create(Window owner, String goalTitle, List<Task> candidates, LocalDate today) {
        Map<Task, BooleanProperty> selected = new LinkedHashMap<>();
        for (Task task : candidates) {
            selected.put(task, new SimpleBooleanProperty(false));
        }
        ListView<Task> list = new ListView<>();
        list.getItems().setAll(candidates);
        list.setPrefSize(520, 360);
        StringConverter<Task> text = new StringConverter<>() {
            @Override
            public String toString(Task task) {
                String day = task.getScheduledDate() == null ? "sin fecha" : Formats.relativeDay(task.getScheduledDate(), today);
                return task.getTitle() + "  ·  " + task.getArea().getDisplayName() + " · " + day;
            }

            @Override
            public Task fromString(String string) {
                return null;
            }
        };
        list.setCellFactory(CheckBoxListCell.forListView(selected::get, text));
        Label empty = new Label("No hay tareas abiertas sin objetivo.\nCrea una con «Nueva tarea».");
        empty.getStyleClass().add("placeholder");
        list.setPlaceholder(empty);

        // Filtro rápido por texto
        TextField filter = new TextField();
        filter.setPromptText("Filtrar…");
        filter.textProperty().addListener((obs, old, value) -> {
            String needle = value == null ? "" : value.trim().toLowerCase();
            list.getItems().setAll(candidates.stream()
                    .filter(t -> needle.isEmpty() || t.getTitle().toLowerCase().contains(needle)).toList());
        });

        Label hint = new Label("Marca las tareas que contribuyen a «" + goalTitle + "». Solo aparecen tareas abiertas, "
                + "sin objetivo y que no son de rutinas.");
        hint.setWrapText(true);
        hint.getStyleClass().addAll("text-muted", "text-small");
        hint.setMaxWidth(520);

        Dialog<List<Long>> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle("Vincular tareas");
        dialog.setHeaderText("Vincular tareas al objetivo");
        ButtonType link = new ButtonType("Vincular", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(link, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(new VBox(8, hint, filter, list));
        dialog.getDialogPane().lookupButton(link).disableProperty().bind(Bindings.createBooleanBinding(
                () -> selected.values().stream().noneMatch(BooleanProperty::get),
                selected.values().toArray(BooleanProperty[]::new)));
        Theme.style(dialog.getDialogPane());
        dialog.setResultConverter(button -> button != link ? null
                : selected.entrySet().stream().filter(e -> e.getValue().get()).map(e -> e.getKey().getId()).toList());
        return dialog;
    }
}
