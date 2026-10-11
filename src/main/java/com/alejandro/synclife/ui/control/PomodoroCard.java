package com.alejandro.synclife.ui.control;

import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.service.PomodoroCycle.Phase;
import com.alejandro.synclife.ui.pomodoro.PomodoroTimer;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Tarjeta del Pomodoro, fija en la barra lateral: fase, cuenta atrás, tarea y botones.
 * Solo se ve cuando hay una tarea en el Pomodoro (se inicia desde el menú de una tarea).
 */
public class PomodoroCard extends VBox {

    private static final PseudoClass FOCUS = PseudoClass.getPseudoClass("focus-phase");
    private static final PseudoClass BREAK = PseudoClass.getPseudoClass("break-phase");
    private static final PseudoClass PAUSED = PseudoClass.getPseudoClass("paused");

    private final PomodoroTimer timer;
    private final Label phaseLabel = new Label();
    private final Label countLabel = new Label();
    private final Label remainingLabel = new Label();
    private final Label taskLabel = new Label();
    private final ProgressBar progress = new ProgressBar(0);
    private final Button primary = new Button();
    private final Button secondary = new Button();
    private final Button close = new Button(null, new FontIcon("mdi2c-close"));

    public PomodoroCard(PomodoroTimer timer) {
        this.timer = timer;
        getStyleClass().add("pomodoro-card");
        setSpacing(6);

        FontIcon icon = new FontIcon("mdi2t-timer-outline");
        icon.getStyleClass().add("pomodoro-icon");
        phaseLabel.setGraphic(icon);
        phaseLabel.getStyleClass().add("pomodoro-phase");
        countLabel.getStyleClass().addAll("text-muted", "text-small");
        close.getStyleClass().addAll("button-icon", "flat", "small");
        close.setTooltip(new Tooltip("Cerrar"));
        close.setOnAction(e -> timer.dismiss());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(6, phaseLabel, countLabel, spacer, close);
        header.setAlignment(Pos.CENTER_LEFT);

        remainingLabel.getStyleClass().add("pomodoro-remaining");
        taskLabel.getStyleClass().add("pomodoro-task");
        taskLabel.setWrapText(true);
        taskLabel.setMaxHeight(36); // como mucho dos líneas
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.getStyleClass().add("small");

        // La barra lateral es estrecha: un botón con texto y otro solo con icono (con su descripción)
        primary.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(primary, Priority.ALWAYS);
        secondary.getStyleClass().add("button-icon");
        secondary.setMinWidth(Region.USE_PREF_SIZE);
        HBox buttons = new HBox(6, primary, secondary);

        getChildren().addAll(header, remainingLabel, taskLabel, progress, buttons);

        timer.phaseProperty().addListener((obs, old, value) -> refresh());
        timer.pausedProperty().addListener((obs, old, value) -> refresh());
        timer.taskProperty().addListener((obs, old, value) -> refresh());
        remainingLabel.textProperty().bind(timer.remainingProperty());
        progress.progressProperty().bind(timer.progressProperty());
        refresh();
    }

    private void refresh() {
        Task task = timer.taskProperty().get();
        Phase phase = timer.phaseProperty().get();
        boolean paused = timer.pausedProperty().get();
        setVisible(task != null);
        setManaged(task != null);
        if (task == null) {
            return;
        }
        taskLabel.setText(task.getTitle());
        Tooltip.install(taskLabel, new Tooltip(task.getTitle()));
        phaseLabel.setText(phase == Phase.IDLE ? "Pomodoro" : phase.getDisplayName());
        int done = timer.completedFocus();
        countLabel.setText(done == 0 ? "" : done + (done == 1 ? " hecho" : " hechos"));
        close.setVisible(phase == Phase.IDLE);
        close.setManaged(phase == Phase.IDLE);
        remainingLabel.setVisible(phase != Phase.IDLE);
        remainingLabel.setManaged(phase != Phase.IDLE);
        progress.setVisible(phase != Phase.IDLE);
        progress.setManaged(phase != Phase.IDLE);

        pseudoClassStateChanged(FOCUS, phase == Phase.FOCUS);
        pseudoClassStateChanged(BREAK, phase.isBreak());
        pseudoClassStateChanged(PAUSED, paused);

        switch (phase) {
            case FOCUS -> {
                set(primary, paused ? "Seguir" : "Pausar", paused ? "mdi2p-play" : "mdi2p-pause", timer::togglePause);
                set(secondary, null, "mdi2s-stop", timer::stop);
                secondary.setTooltip(new Tooltip("Terminar: registra el tiempo hecho (si es más de 1 min)"));
            }
            case SHORT_BREAK, LONG_BREAK -> {
                if (paused) {
                    set(primary, "Descansar", "mdi2c-coffee-outline", timer::togglePause);
                } else {
                    set(primary, "Pausar", "mdi2p-pause", timer::togglePause);
                }
                set(secondary, null, "mdi2s-skip-next", timer::skipBreak);
                secondary.setTooltip(new Tooltip("Saltar el descanso"));
            }
            case IDLE -> {
                set(primary, "Otro pomodoro", "mdi2p-play", timer::again);
                primary.setTooltip(new Tooltip("Otro pomodoro con esta tarea"));
                secondary.setVisible(false);
                secondary.setManaged(false);
                return;
            }
        }
        secondary.setVisible(true);
        secondary.setManaged(true);
    }

    private static void set(Button button, String text, String icon, Runnable action) {
        button.setText(text);
        button.setGraphic(new FontIcon(icon));
        button.setTooltip(null);
        button.setOnAction(e -> action.run());
    }
}
