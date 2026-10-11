package com.alejandro.synclife.controller;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.ui.AppUi;
import com.alejandro.synclife.ui.Formats;
import com.alejandro.synclife.ui.Navigator;
import com.alejandro.synclife.ui.View;
import com.alejandro.synclife.ui.control.PomodoroCard;
import com.alejandro.synclife.ui.control.TaskEditorDialog;
import com.alejandro.synclife.ui.notify.NotificationCenter;
import com.alejandro.synclife.ui.pomodoro.PomodoroTimer;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.EnumMap;
import java.util.Map;

/**
 * Ventana principal: barra lateral de navegación con el Pomodoro, atajos de teclado, zona central
 * intercambiable y capa de avisos.
 */
public class MainController {

    private final AppContext context;
    private final ToggleGroup navGroup = new ToggleGroup();
    private final Map<View, ToggleButton> navButtons = new EnumMap<>(View.class);
    private Navigator navigator;
    private PomodoroTimer pomodoro;

    @FXML private Label todayLabel;
    @FXML private VBox primaryNav;
    @FXML private VBox secondaryNav;
    @FXML private VBox pomodoroHost;
    @FXML private StackPane contentHost;
    @FXML private VBox toastLayer;

    public MainController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        navigator = new Navigator(context, contentHost);
        pomodoro = new PomodoroTimer(context);
        AppUi.install(navigator, new NotificationCenter(toastLayer), pomodoro);
        pomodoroHost.getChildren().add(new PomodoroCard(pomodoro));
        todayLabel.setText(Formats.dayTitle(context.today()));

        for (View view : View.values()) {
            ToggleButton button = new ToggleButton(view.title(), new FontIcon(view.iconLiteral()));
            button.getStyleClass().add("nav-button");
            button.setMaxWidth(Double.MAX_VALUE);
            button.setToggleGroup(navGroup);
            button.setTooltip(new Tooltip(view.title() + " (" + view.shortcutText() + ")"));
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

    /** Se llama cuando la escena ya está en la ventana: atajos de teclado y vista inicial. */
    public void start(Scene scene, View initialView) {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN),
                this::newTask);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F5), navigator::refreshCurrent);
        for (View view : View.values()) {
            scene.getAccelerators().put(new KeyCodeCombination(view.shortcut(), KeyCombination.SHORTCUT_DOWN),
                    () -> navigator.navigate(view));
        }
        if (scene.getWindow() instanceof Stage stage) {
            pomodoro.attach(stage);
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

    public PomodoroTimer pomodoro() {
        return pomodoro;
    }
}
