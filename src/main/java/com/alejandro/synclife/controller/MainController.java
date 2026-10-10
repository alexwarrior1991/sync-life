package com.alejandro.synclife.controller;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.ui.Formats;
import com.alejandro.synclife.ui.Navigator;
import com.alejandro.synclife.ui.View;
import com.alejandro.synclife.ui.control.TaskEditorDialog;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.EnumMap;
import java.util.Map;

/** Ventana principal: barra lateral de navegación, atajos de teclado y zona central intercambiable. */
public class MainController {

    private static final KeyCode[] DIGITS = {
            KeyCode.DIGIT1, KeyCode.DIGIT2, KeyCode.DIGIT3, KeyCode.DIGIT4,
            KeyCode.DIGIT5, KeyCode.DIGIT6, KeyCode.DIGIT7};

    private final AppContext context;
    private final ToggleGroup navGroup = new ToggleGroup();
    private final Map<View, ToggleButton> navButtons = new EnumMap<>(View.class);
    private Navigator navigator;

    @FXML private Label todayLabel;
    @FXML private VBox primaryNav;
    @FXML private VBox secondaryNav;
    @FXML private StackPane contentHost;

    public MainController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        navigator = new Navigator(context, contentHost);
        todayLabel.setText(Formats.dayTitle(context.today()));

        for (View view : View.values()) {
            ToggleButton button = new ToggleButton(view.title(), new FontIcon(view.iconLiteral()));
            button.getStyleClass().add("nav-button");
            button.setMaxWidth(Double.MAX_VALUE);
            button.setToggleGroup(navGroup);
            button.setOnAction(e -> navigator.navigate(view));
            navButtons.put(view, button);
            (view.isPrimary() ? primaryNav : secondaryNav).getChildren().add(button);
        }
        // Siempre hay una sección seleccionada
        navGroup.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == null && old != null) {
                navGroup.selectToggle(old);
            }
        });
        navigator.currentProperty().addListener((obs, old, view) -> {
            Toggle button = navButtons.get(view);
            if (navGroup.getSelectedToggle() != button) {
                navGroup.selectToggle(button);
            }
        });
    }

    /** Se llama cuando la escena ya existe: atajos de teclado y vista inicial. */
    public void start(Scene scene, View initialView) {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN),
                this::newTask);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F5), navigator::refreshCurrent);
        View[] views = View.values();
        for (int i = 0; i < views.length && i < DIGITS.length; i++) {
            View view = views[i];
            scene.getAccelerators().put(new KeyCodeCombination(DIGITS[i], KeyCombination.SHORTCUT_DOWN),
                    () -> navigator.navigate(view));
        }
        navigator.navigate(initialView);
    }

    public void newTask() {
        TaskEditorDialog.open(contentHost.getScene().getWindow(), context, null, context.today(),
                navigator::refreshCurrent);
    }

    public Navigator navigator() {
        return navigator;
    }
}
