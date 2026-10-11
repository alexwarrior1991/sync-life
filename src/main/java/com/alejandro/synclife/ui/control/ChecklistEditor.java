package com.alejandro.synclife.ui.control;

import com.alejandro.synclife.model.ChecklistItem;
import com.alejandro.synclife.service.TaskService;
import com.alejandro.synclife.ui.Ui;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;

/**
 * Editor de la lista de comprobación de una tarea: cada paso con su casilla, texto editable, subir,
 * bajar y quitar; abajo, un campo para añadir pasos con Enter. Los cambios se guardan con la tarea.
 */
public class ChecklistEditor extends VBox {

    private static final PseudoClass DONE = PseudoClass.getPseudoClass("done");

    private final VBox rows = new VBox(2);
    private final TextField addField = new TextField();
    private final ReadOnlyStringWrapper summary = new ReadOnlyStringWrapper(this, "summary", "");

    public ChecklistEditor() {
        getStyleClass().add("checklist-editor");
        setSpacing(6);
        Ui.limitLength(addField, 200);
        addField.setPromptText("Añadir paso… (Enter)");
        addField.getStyleClass().add("checklist-add");
        addField.setOnAction(e -> addFromField());
        Button add = new Button(null, new FontIcon("mdi2p-plus"));
        add.getStyleClass().addAll("button-icon", "flat", "small");
        add.setTooltip(new Tooltip("Añadir paso"));
        add.setOnAction(e -> addFromField());
        HBox addRow = new HBox(4, addField, add);
        addRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(addField, Priority.ALWAYS);
        getChildren().addAll(rows, addRow);
        updateRows();
    }

    public void setItems(List<ChecklistItem> items) {
        rows.getChildren().clear();
        items.forEach(this::addRow);
        updateRows();
    }

    /** Los pasos en su orden actual, sin los que se hayan quedado vacíos. */
    public List<ChecklistItem> getItems() {
        List<ChecklistItem> items = new ArrayList<>();
        for (var node : rows.getChildren()) {
            Row row = (Row) node;
            String title = row.title.getText() == null ? "" : row.title.getText().trim();
            if (!title.isEmpty()) {
                items.add(new ChecklistItem(row.id, title, row.check.isSelected()));
            }
        }
        // Lo escrito en el campo de añadir y no confirmado con Enter también cuenta
        String pending = addField.getText() == null ? "" : addField.getText().trim();
        if (!pending.isEmpty() && items.size() < TaskService.MAX_CHECKLIST_ITEMS) {
            items.add(new ChecklistItem(null, pending, false));
        }
        return items;
    }

    /** "2/5", o vacío si no hay pasos. */
    public ReadOnlyStringProperty summaryProperty() {
        return summary.getReadOnlyProperty();
    }

    public int size() {
        return rows.getChildren().size();
    }

    /** Añade un paso nuevo (lo usa también la prueba de humo). */
    public void add(String title) {
        if (title == null || title.isBlank() || size() >= TaskService.MAX_CHECKLIST_ITEMS) {
            return;
        }
        addRow(new ChecklistItem(null, title.trim(), false));
        updateRows();
    }

    private void addFromField() {
        String text = addField.getText();
        if (text == null || text.isBlank()) {
            return;
        }
        if (size() >= TaskService.MAX_CHECKLIST_ITEMS) {
            addField.setPromptText("Máximo " + TaskService.MAX_CHECKLIST_ITEMS + " pasos");
            return;
        }
        add(text);
        addField.clear();
        addField.requestFocus();
    }

    private void addRow(ChecklistItem item) {
        Row row = new Row(item);
        rows.getChildren().add(row);
    }

    private void move(Row row, int delta) {
        int index = rows.getChildren().indexOf(row);
        int target = index + delta;
        if (index < 0 || target < 0 || target >= rows.getChildren().size()) {
            return;
        }
        rows.getChildren().remove(index);
        rows.getChildren().add(target, row);
        updateRows();
    }

    private void updateRows() {
        int total = rows.getChildren().size();
        int done = 0;
        for (int i = 0; i < total; i++) {
            Row row = (Row) rows.getChildren().get(i);
            row.up.setDisable(i == 0);
            row.down.setDisable(i == total - 1);
            if (row.check.isSelected()) {
                done++;
            }
        }
        summary.set(total == 0 ? "" : done + "/" + total);
        rows.setVisible(total > 0);
        rows.setManaged(total > 0);
    }

    /** Un paso de la lista. */
    private final class Row extends HBox {
        private final Long id;
        private final CheckBox check = new CheckBox();
        private final TextField title = new TextField();
        private final Button up = iconButton("mdi2a-arrow-up", "Subir");
        private final Button down = iconButton("mdi2a-arrow-down", "Bajar");
        private final Button remove = iconButton("mdi2c-close", "Quitar paso");

        private Row(ChecklistItem item) {
            super(4);
            this.id = item.id();
            getStyleClass().add("checklist-row");
            setAlignment(Pos.CENTER_LEFT);
            check.setSelected(item.done());
            title.setText(item.title());
            Ui.limitLength(title, 200);
            title.getStyleClass().add("checklist-title");
            HBox.setHgrow(title, Priority.ALWAYS);
            pseudoClassStateChanged(DONE, item.done());

            check.selectedProperty().addListener((obs, old, value) -> {
                pseudoClassStateChanged(DONE, value);
                updateRows();
            });
            up.setOnAction(e -> move(this, -1));
            down.setOnAction(e -> move(this, 1));
            remove.setOnAction(e -> {
                rows.getChildren().remove(this);
                updateRows();
            });
            // Enter pasa al campo de añadir; Alt+↑/↓ reordena sin ratón
            title.setOnAction(e -> addField.requestFocus());
            title.setOnKeyPressed(e -> {
                if (e.isAltDown() && e.getCode() == KeyCode.UP) {
                    move(this, -1);
                    title.requestFocus();
                    e.consume();
                } else if (e.isAltDown() && e.getCode() == KeyCode.DOWN) {
                    move(this, 1);
                    title.requestFocus();
                    e.consume();
                }
            });
            getChildren().addAll(check, title, up, down, remove);
        }
    }

    private static Button iconButton(String literal, String tooltip) {
        Button button = new Button(null, new FontIcon(literal));
        button.getStyleClass().addAll("button-icon", "flat", "small");
        button.setTooltip(new Tooltip(tooltip));
        button.setFocusTraversable(false);
        return button;
    }
}
