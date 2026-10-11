package com.alejandro.synclife.ui.pomodoro;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.config.AppSettings.PomodoroSettings;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.TimeSource;
import com.alejandro.synclife.service.PomodoroCycle;
import com.alejandro.synclife.service.PomodoroCycle.FocusSession;
import com.alejandro.synclife.service.PomodoroCycle.Phase;
import com.alejandro.synclife.service.PomodoroCycle.Transition;
import com.alejandro.synclife.ui.AppUi;
import com.alejandro.synclife.ui.Dialogs;
import com.alejandro.synclife.ui.Formats;
import com.alejandro.synclife.ui.FxAsync;
import com.alejandro.synclife.ui.SmokeRunner;
import com.alejandro.synclife.ui.notify.NotificationCenter;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pomodoro de la ventana: une {@link PomodoroCycle} (la lógica) con un {@link Timeline} de un segundo,
 * guarda cada sesión de enfoque como tiempo registrado de la tarea y expone propiedades para la
 * tarjeta de la barra lateral. Se usa solo desde el hilo de JavaFX.
 */
public class PomodoroTimer {

    private static final Logger LOG = Logger.getLogger(PomodoroTimer.class.getName());
    private static final String APP_TITLE = "SyncLife";

    private final AppContext context;
    private final PomodoroCycle cycle;
    private final Timeline ticker = new Timeline(new KeyFrame(Duration.seconds(1), e -> tick()));

    private final ReadOnlyObjectWrapper<Task> task = new ReadOnlyObjectWrapper<>(this, "task");
    private final ReadOnlyObjectWrapper<Phase> phase = new ReadOnlyObjectWrapper<>(this, "phase", Phase.IDLE);
    private final ReadOnlyBooleanWrapper paused = new ReadOnlyBooleanWrapper(this, "paused");
    private final ReadOnlyStringWrapper remaining = new ReadOnlyStringWrapper(this, "remaining", "");
    private final ReadOnlyDoubleWrapper progress = new ReadOnlyDoubleWrapper(this, "progress");
    private Stage stage;

    public PomodoroTimer(AppContext context) {
        this.context = context;
        this.cycle = new PomodoroCycle(toCycleSettings(context.settings().getPomodoro()));
        ticker.setCycleCount(Animation.INDEFINITE);
        update();
    }

    /** La ventana cuyo título muestra la cuenta atrás. */
    public void attach(Stage window) {
        this.stage = window;
        update();
    }

    /** Vuelve a leer los ajustes; se aplican desde la siguiente fase. */
    public void reloadSettings() {
        cycle.setSettings(toCycleSettings(context.settings().getPomodoro()));
        update();
    }

    // ---------------------------------------------------------------- acciones

    /**
     * Empieza un enfoque con {@code target}. Si ya había otro en marcha con otra tarea, pregunta y
     * guarda el tiempo de ese antes de cambiar. La tarea pasa a "En progreso".
     */
    public void start(Task target) {
        Task current = task.get();
        if (isActive() && current != null && current.getId().equals(target.getId())) {
            notifications().info("El pomodoro de «" + target.getTitle() + "» ya está en marcha.");
            return;
        }
        if (isActive() && phase.get() == Phase.FOCUS && current != null
                && !Dialogs.confirm("¿Cambiar el pomodoro de tarea?",
                "Se guardará el tiempo que llevas en «" + current.getTitle() + "» y empezará uno nuevo con «"
                        + target.getTitle() + "».", "Cambiar")) {
            return;
        }
        if (isActive()) {
            stop();
        }
        FxAsync.run(() -> context.tasks().startWorking(target.getId()), working -> {
            task.set(working);
            cycle.start(now());
            ticker.play();
            update();
            refreshViews();
            notifications().show(NotificationCenter.Kind.INFO, "mdi2t-timer-outline",
                    "Pomodoro iniciado: " + Formats.duration((int) cycle.length().toMinutes())
                            + " de enfoque\n«" + working.getTitle() + "»", Duration.seconds(4));
        }, error -> Dialogs.error(error));
    }

    /** Otro enfoque con la misma tarea (tras un descanso). */
    public void again() {
        Task current = task.get();
        if (current != null && !isActive()) {
            start(current);
        }
    }

    public void togglePause() {
        if (cycle.isRunning()) {
            cycle.pause(now());
        } else if (cycle.isPaused()) {
            cycle.resume(now());
            ticker.play();
        }
        update();
    }

    /** Termina el ciclo; si se estaba en un enfoque de al menos un minuto, registra ese tiempo. */
    public void stop() {
        cycle.stop(now()).ifPresent(this::record);
        ticker.stop();
        update();
    }

    public void skipBreak() {
        cycle.skipBreak(now());
        ticker.stop();
        update();
    }

    /** Quita la tarjeta (solo cuando no hay nada en marcha). */
    public void dismiss() {
        if (!isActive()) {
            task.set(null);
            update();
        }
    }

    /** Al cerrar la aplicación: guarda la sesión de enfoque a medias antes de que se pare el hilo de BD. */
    public void shutdown() {
        ticker.stop();
        cycle.stop(now()).ifPresent(this::record);
    }

    // ---------------------------------------------------------------- estado

    public boolean isActive() {
        return cycle.phase() != Phase.IDLE;
    }

    /** ¿Es {@code candidate} la tarea del pomodoro en marcha? */
    public boolean isWorkingOn(Task candidate) {
        Task current = task.get();
        return isActive() && current != null && candidate != null && current.getId().equals(candidate.getId());
    }

    public int completedFocus() {
        return cycle.completedFocus();
    }

    public int longBreakEvery() {
        return cycle.getSettings().longBreakEvery();
    }

    public ReadOnlyObjectProperty<Task> taskProperty() {
        return task.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<Phase> phaseProperty() {
        return phase.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty pausedProperty() {
        return paused.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty remainingProperty() {
        return remaining.getReadOnlyProperty();
    }

    public ReadOnlyDoubleProperty progressProperty() {
        return progress.getReadOnlyProperty();
    }

    // ---------------------------------------------------------------- internos

    private void tick() {
        cycle.tick(now()).ifPresent(this::onTransition);
        if (!cycle.isRunning()) {
            ticker.stop();
        }
        update();
    }

    private void onTransition(Transition transition) {
        Task current = task.get();
        String title = current == null ? "" : "«" + current.getTitle() + "»";
        transition.focusSession().ifPresent(session -> {
            record(session);
            String next = transition.to() == Phase.LONG_BREAK ? "descanso largo" : "descanso";
            String breakText = cycle.isRunning()
                    ? "Empieza el " + next + " de " + Formats.duration((int) cycle.length().toMinutes()) + "."
                    : "Cuando quieras, empieza el " + next + ".";
            notifications().show(NotificationCenter.Kind.SUCCESS, "mdi2c-check-circle-outline",
                    "¡Pomodoro terminado! " + session.minutes() + " min registrados en " + title + "\n" + breakText,
                    Duration.seconds(10));
        });
        if (transition.to() == Phase.IDLE) {
            Button again = new Button("Otro pomodoro", new FontIcon("mdi2p-play"));
            again.setOnAction(e -> again());
            notifications().show(NotificationCenter.Kind.INFO, "mdi2c-coffee-outline",
                    "Descanso terminado. ¿Seguimos con " + title + "?", Duration.seconds(30), again);
        }
        beep();
    }

    private void record(FocusSession session) {
        Task current = task.get();
        if (current == null) {
            return;
        }
        long taskId = current.getId();
        LocalDateTime start = LocalDateTime.ofInstant(session.start(), context.clock().getZone());
        LocalDateTime end = LocalDateTime.ofInstant(session.end(), context.clock().getZone());
        int minutes = session.minutes();
        FxAsync.run(() -> context.timeTracking().record(taskId, start, end, minutes, TimeSource.POMODORO),
                entry -> refreshViews(),
                error -> {
                    // En segundo plano: se avisa sin interrumpir con un diálogo
                    LOG.log(Level.WARNING, "No se pudo registrar el tiempo del pomodoro", error);
                    if (AppUi.isInstalled()) {
                        notifications().warning("No se pudo guardar el tiempo del pomodoro (" + minutes
                                + " min): " + error.getMessage());
                    }
                });
    }

    private void update() {
        Instant now = now();
        phase.set(cycle.phase());
        paused.set(cycle.isPaused());
        remaining.set(isActive() ? Formats.countdown(cycle.remaining(now)) : "");
        progress.set(cycle.progress(now));
        if (stage != null) {
            stage.setTitle(isActive()
                    ? (cycle.isPaused() ? "⏸ " : "") + remaining.get() + " · " + cycle.phase().getDisplayName()
                    + " · " + APP_TITLE
                    : APP_TITLE);
        }
    }

    private void refreshViews() {
        if (AppUi.isInstalled()) {
            AppUi.navigator().refreshCurrent();
        }
    }

    private void beep() {
        if (!context.settings().getPomodoro().sound() || SmokeRunner.enabled()) {
            return;
        }
        try {
            java.awt.Toolkit.getDefaultToolkit().beep();
        } catch (Throwable e) {
            LOG.log(Level.FINE, "Sin sonido disponible", e);
        }
    }

    private NotificationCenter notifications() {
        return AppUi.notifications();
    }

    private Instant now() {
        return context.clock().instant();
    }

    private static PomodoroCycle.Settings toCycleSettings(PomodoroSettings s) {
        return PomodoroCycle.Settings.ofMinutes(s.focusMinutes(), s.shortBreakMinutes(), s.longBreakMinutes(),
                s.longBreakEvery(), s.autoStartBreaks());
    }
}
