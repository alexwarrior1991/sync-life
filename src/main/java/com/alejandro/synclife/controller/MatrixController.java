package com.alejandro.synclife.controller;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.Quadrant;
import com.alejandro.synclife.service.TaskService.MatrixData;
import com.alejandro.synclife.ui.FxAsync;
import com.alejandro.synclife.ui.TaskActions;
import com.alejandro.synclife.ui.TaskDragAndDrop;
import com.alejandro.synclife.ui.Ui;
import com.alejandro.synclife.ui.ViewController;
import com.alejandro.synclife.ui.cell.TaskListCell;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Pantalla "Matriz": las tareas abiertas en los cuatro cuadrantes de Eisenhower (urgente / importante)
 * y un carril con las que aún no están clasificadas. Arrastrar una tarea a otro cuadrante cambia sus marcas.
 */
public class MatrixController implements ViewController {

    /** Lista y contador de un cuadrante. */
    private record QuadrantPane(ListView<Task> list, Label count) {
    }

    private final AppContext context;
    private final Map<Quadrant, QuadrantPane> panes = new EnumMap<>(Quadrant.class);
    private final ToggleGroup areaGroup = new ToggleGroup();
    private TaskActions actions;

    @FXML private Label subtitle;
    @FXML private HBox areaFilterBox;
    @FXML private GridPane grid;
    @FXML private ListView<Task> unclassifiedList;
    @FXML private Label unclassifiedCount;

    public MatrixController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        actions = new TaskActions(context, grid, this::onShow);
        buildAreaFilter();
        buildGrid();

        unclassifiedList.setCellFactory(list -> new TaskListCell(actions, true));
        actions.installDeleteKey(unclassifiedList);
        unclassifiedList.setPlaceholder(placeholder("Todo clasificado"));
        TaskDragAndDrop.installDropTarget(unclassifiedList, id -> actions.setQuadrant(id, null));
    }

    private void buildAreaFilter() {
        // El trabajo es lo principal de la matriz; "Todas" incluye deporte y vida
        ToggleButton work = new ToggleButton(Area.TRABAJO.getDisplayName());
        work.getStyleClass().add("left-pill");
        work.setUserData(Area.TRABAJO);
        ToggleButton all = new ToggleButton("Todas");
        all.getStyleClass().add("right-pill");
        for (ToggleButton button : List.of(work, all)) {
            button.setToggleGroup(areaGroup);
            areaFilterBox.getChildren().add(button);
        }
        work.setSelected(true);
        areaGroup.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                areaGroup.selectToggle(old);
            } else {
                onShow();
            }
        });
    }

    private void buildGrid() {
        ColumnConstraints axis = new ColumnConstraints(26);
        ColumnConstraints left = new ColumnConstraints();
        left.setHgrow(Priority.ALWAYS);
        left.setMinWidth(0);
        left.setPrefWidth(300);
        ColumnConstraints right = new ColumnConstraints();
        right.setHgrow(Priority.ALWAYS);
        right.setMinWidth(0);
        right.setPrefWidth(300);
        grid.getColumnConstraints().addAll(axis, left, right);
        RowConstraints header = new RowConstraints();
        RowConstraints top = new RowConstraints();
        top.setVgrow(Priority.ALWAYS);
        top.setPrefHeight(300);
        top.setMinHeight(0);
        RowConstraints bottom = new RowConstraints();
        bottom.setVgrow(Priority.ALWAYS);
        bottom.setPrefHeight(300);
        bottom.setMinHeight(0);
        grid.getRowConstraints().addAll(header, top, bottom);

        grid.add(axisLabel("URGENTE", false), 1, 0);
        grid.add(axisLabel("NO URGENTE", false), 2, 0);
        grid.add(axisLabel("IMPORTANTE", true), 0, 1);
        grid.add(axisLabel("NO IMPORTANTE", true), 0, 2);

        for (Quadrant quadrant : Quadrant.values()) {
            int column = quadrant.isUrgent() ? 1 : 2;
            int row = quadrant.isImportant() ? 1 : 2;
            grid.add(quadrantPane(quadrant), column, row);
        }
    }

    private VBox quadrantPane(Quadrant quadrant) {
        FontIcon icon = new FontIcon(quadrant.getIconLiteral());
        icon.setIconColor(Color.web(quadrant.getColorHex()));
        icon.getStyleClass().add("quadrant-icon");
        Label name = new Label(quadrant.getDisplayName(), icon);
        name.getStyleClass().add("title-4");
        Label description = new Label(quadrant.getDescription());
        description.getStyleClass().addAll("text-muted", "text-small");
        Label count = new Label();
        count.getStyleClass().addAll("badge", "badge-muted");
        HBox header = new HBox(8, name, description, Ui.hSpacer(), count);
        header.setAlignment(Pos.CENTER_LEFT);

        // Celdas compactas, como en la Semana: el título se ve entero aunque el cuadrante sea estrecho
        ListView<Task> list = new ListView<>();
        list.setCellFactory(l -> new TaskListCell(actions, true));
        list.setPlaceholder(placeholder(emptyText(quadrant)));
        actions.installDeleteKey(list);
        TaskDragAndDrop.installDropTarget(list, id -> actions.setQuadrant(id, quadrant));
        VBox.setVgrow(list, Priority.ALWAYS);

        VBox pane = new VBox(6, header, list);
        pane.getStyleClass().addAll("panel", "quadrant-pane", "quadrant-" + quadrant.name().toLowerCase());
        pane.setStyle("-fx-border-color: " + quadrant.getColorHex() + "66;");
        pane.setMinWidth(0);
        panes.put(quadrant, new QuadrantPane(list, count));
        return pane;
    }

    private static String emptyText(Quadrant quadrant) {
        return switch (quadrant) {
            case HACER_YA -> "Nada urgente e importante. ¡Bien!";
            case PLANIFICAR -> "Aquí va lo importante que conviene planificar";
            case DELEGAR -> "Urgente pero no importante: ¿quién más puede hacerlo?";
            case ELIMINAR -> "Lo que puedes dejar de hacer";
        };
    }

    private static javafx.scene.Node axisLabel(String text, boolean vertical) {
        Label label = new Label(text);
        label.getStyleClass().add("matrix-axis");
        if (!vertical) {
            GridPane.setHalignment(label, HPos.CENTER);
            return label;
        }
        label.setRotate(-90);
        Group group = new Group(label); // el Group hace que el layout use el tamaño ya girado
        GridPane.setHalignment(group, HPos.CENTER);
        return group;
    }

    @Override
    public void onShow() {
        Area area = (Area) areaGroup.getSelectedToggle().getUserData();
        FxAsync.run(() -> context.tasks().matrix(area), data -> {
            if (area == areaGroup.getSelectedToggle().getUserData()) {
                apply(data, area);
            }
        });
    }

    private void apply(MatrixData data, Area area) {
        int total = data.unclassified().size();
        for (Quadrant quadrant : Quadrant.values()) {
            List<Task> tasks = data.of(quadrant);
            QuadrantPane pane = panes.get(quadrant);
            pane.list().getItems().setAll(tasks);
            pane.count().setText(String.valueOf(tasks.size()));
            total += tasks.size();
        }
        unclassifiedList.getItems().setAll(data.unclassified());
        unclassifiedCount.setText(String.valueOf(data.unclassified().size()));
        String scope = area == null ? "en todas las áreas" : "de " + area.getDisplayName().toLowerCase();
        subtitle.setText(total + (total == 1 ? " tarea abierta " : " tareas abiertas ") + scope
                + (data.unclassified().isEmpty() ? "" : " · " + data.unclassified().size() + " sin clasificar")
                + " · Arrastra para reclasificar");
    }

    @FXML
    private void newTask() {
        actions.create(context.today());
    }

    private static Label placeholder(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("placeholder");
        label.setWrapText(true);
        return label;
    }
}
