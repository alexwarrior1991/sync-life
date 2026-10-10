package com.alejandro.synclife.controller;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.model.entity.Category;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.ui.Dialogs;
import com.alejandro.synclife.ui.FxAsync;
import com.alejandro.synclife.ui.Theme;
import com.alejandro.synclife.ui.Ui;
import com.alejandro.synclife.ui.ViewController;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Pantalla "Categorías": una tarjeta por área con sus categorías, alta rápida, edición y borrado. */
public class CategoriesController implements ViewController {

    private record Data(Map<Area, List<Category>> grouped, Map<Long, Long> openCounts) {
    }

    /** Controles de la tarjeta de un área. */
    private record AreaCard(ListView<Category> list, Label count, TextField name, ColorPicker color) {
    }

    private final AppContext context;
    private final Map<Area, AreaCard> cards = new EnumMap<>(Area.class);
    private Map<Long, Long> openCounts = Map.of();

    @FXML private CheckBox showArchived;
    @FXML private HBox areasBox;

    public CategoriesController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        for (Area area : Area.values()) {
            areasBox.getChildren().add(buildCard(area));
        }
        showArchived.selectedProperty().addListener((obs, old, value) -> onShow());
    }

    private VBox buildCard(Area area) {
        FontIcon icon = new FontIcon(area.getIconLiteral());
        icon.setIconColor(Color.web(area.getColorHex()));
        Label title = new Label(area.getDisplayName(), icon);
        title.getStyleClass().add("title-4");
        Label count = new Label();
        count.getStyleClass().add("text-muted");
        HBox header = new HBox(8, title, Ui.hSpacer(), count);
        header.setAlignment(Pos.CENTER_LEFT);

        ListView<Category> list = new ListView<>();
        list.setCellFactory(l -> new CategoryCell());
        Label empty = new Label("Sin categorías");
        empty.getStyleClass().add("placeholder");
        list.setPlaceholder(empty);
        VBox.setVgrow(list, Priority.ALWAYS);

        TextField name = new TextField();
        name.setPromptText("Nueva categoría…");
        Ui.limitLength(name, 80);
        HBox.setHgrow(name, Priority.ALWAYS);
        ColorPicker color = new ColorPicker(Color.web(area.getColorHex()));
        color.getStyleClass().addAll("button", "compact-picker");
        Button add = new Button(null, new FontIcon("mdi2p-plus"));
        add.getStyleClass().add("accent");
        HBox addRow = new HBox(6, name, color, add);
        addRow.setAlignment(Pos.CENTER_LEFT);

        AreaCard card = new AreaCard(list, count, name, color);
        cards.put(area, card);
        add.setOnAction(e -> addCategory(area));
        name.setOnAction(e -> addCategory(area));

        VBox box = new VBox(10, header, list, addRow);
        box.getStyleClass().addAll("panel", "area-card");
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setMaxWidth(Double.MAX_VALUE);
        box.setPrefWidth(300);
        return box;
    }

    @Override
    public void onShow() {
        boolean archived = showArchived.isSelected();
        FxAsync.run(() -> new Data(context.categories().groupedByArea(archived), context.categories().openTaskCounts()),
                data -> {
                    openCounts = data.openCounts();
                    data.grouped().forEach((area, categories) -> {
                        AreaCard card = cards.get(area);
                        card.list().getItems().setAll(categories);
                        card.count().setText(categories.size() + (categories.size() == 1 ? " categoría" : " categorías"));
                    });
                });
    }

    private void addCategory(Area area) {
        AreaCard card = cards.get(area);
        String name = card.name().getText();
        if (name == null || name.isBlank()) {
            card.name().requestFocus();
            return;
        }
        Category category = new Category(name, area, Ui.toHex(card.color().getValue()));
        FxAsync.run(() -> context.categories().save(category), saved -> {
            card.name().clear();
            onShow();
        });
    }

    private void edit(Category category) {
        TextField name = new TextField(category.getName());
        Ui.limitLength(name, 80);
        ComboBox<Area> area = new ComboBox<>();
        area.getItems().setAll(Area.values());
        area.setValue(category.getArea());
        ColorPicker color = new ColorPicker(Color.web(category.getColorHex()));
        CheckBox archived = new CheckBox("Archivada (se oculta al crear tareas)");
        archived.setSelected(category.isArchived());

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.addRow(0, new Label("Nombre"), name);
        form.addRow(1, new Label("Área"), area);
        form.addRow(2, new Label("Color"), color);
        form.add(archived, 1, 3);

        Dialog<Category> dialog = new Dialog<>();
        Dialogs.focusedWindow().ifPresent(dialog::initOwner);
        dialog.setTitle("Editar categoría");
        dialog.setHeaderText("Editar «" + category.getName() + "»");
        ButtonType save = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().lookupButton(save).disableProperty().bind(name.textProperty().isEmpty());
        Theme.style(dialog.getDialogPane());
        dialog.setResultConverter(button -> {
            if (button != save) {
                return null;
            }
            category.setName(name.getText());
            category.setArea(area.getValue());
            category.setColorHex(Ui.toHex(color.getValue()));
            category.setArchived(archived.isSelected());
            return category;
        });
        dialog.showAndWait().ifPresent(edited ->
                FxAsync.run(() -> context.categories().save(edited), saved -> onShow(), error -> {
                    Dialogs.error(error);
                    onShow();
                }));
    }

    private void toggleArchived(Category category) {
        category.setArchived(!category.isArchived());
        FxAsync.run(() -> context.categories().save(category), saved -> onShow());
    }

    private void delete(Category category) {
        FxAsync.run(() -> context.categories().countTasks(category.getId()), count -> {
            String detail = count == 0
                    ? "No tiene tareas asociadas."
                    : count + (count == 1 ? " tarea quedará" : " tareas quedarán")
                    + " sin categoría (se mantienen en el área " + category.getArea() + ").";
            if (Dialogs.confirm("¿Eliminar la categoría «" + category.getName() + "»?", detail, "Eliminar")) {
                FxAsync.runVoid(() -> context.categories().delete(category.getId()), this::onShow);
            }
        });
    }

    /** Celda de categoría: color, nombre, nº de tareas abiertas y etiqueta si está archivada. */
    private final class CategoryCell extends ListCell<Category> {

        private CategoryCell() {
            setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && getItem() != null) {
                    edit(getItem());
                }
            });
        }

        @Override
        protected void updateItem(Category category, boolean empty) {
            super.updateItem(category, empty);
            setText(null);
            if (empty || category == null) {
                setGraphic(null);
                setContextMenu(null);
                return;
            }
            Label name = new Label(category.getName());
            long open = openCounts.getOrDefault(category.getId(), 0L);
            Label count = new Label(open == 0 ? "" : open + (open == 1 ? " abierta" : " abiertas"));
            count.getStyleClass().addAll("text-muted", "text-small");
            HBox row = new HBox(8, Ui.dot(category.getColorHex(), 6), name, Ui.hSpacer(), count);
            row.setAlignment(Pos.CENTER_LEFT);
            if (category.isArchived()) {
                Label tag = new Label("Archivada");
                tag.getStyleClass().addAll("badge", "badge-muted");
                row.getChildren().add(tag);
                name.getStyleClass().add("text-muted");
            }
            setGraphic(row);

            MenuItem editItem = new MenuItem("Editar…", new FontIcon("mdi2p-pencil-outline"));
            editItem.setOnAction(e -> edit(category));
            MenuItem archiveItem = new MenuItem(category.isArchived() ? "Desarchivar" : "Archivar",
                    new FontIcon("mdi2a-archive-outline"));
            archiveItem.setOnAction(e -> toggleArchived(category));
            MenuItem deleteItem = new MenuItem("Eliminar", new FontIcon("mdi2d-delete-outline"));
            deleteItem.setOnAction(e -> delete(category));
            setContextMenu(new ContextMenu(editItem, archiveItem, new SeparatorMenuItem(), deleteItem));
        }
    }
}
