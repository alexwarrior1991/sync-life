package com.alejandro.synclife.ui.cell;

import com.alejandro.synclife.model.entity.Category;
import com.alejandro.synclife.ui.Choice;
import com.alejandro.synclife.ui.Ui;
import javafx.scene.control.ListCell;

/** Opción de categoría en un ComboBox, con su punto de color. */
public class CategoryChoiceCell extends ListCell<Choice<Category>> {

    @Override
    protected void updateItem(Choice<Category> choice, boolean empty) {
        super.updateItem(choice, empty);
        if (empty || choice == null) {
            setText(null);
            setGraphic(null);
            return;
        }
        setText(choice.label());
        setGraphic(choice.value() == null ? null : Ui.dot(choice.value().getColorHex(), 5));
    }
}
