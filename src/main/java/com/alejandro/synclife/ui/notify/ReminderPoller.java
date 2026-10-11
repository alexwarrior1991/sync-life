package com.alejandro.synclife.ui.notify;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.TaskStatus;
import com.alejandro.synclife.service.ReminderService.Due;
import com.alejandro.synclife.ui.AppUi;
import com.alejandro.synclife.ui.Dialogs;
import com.alejandro.synclife.ui.Formats;
import com.alejandro.synclife.ui.FxAsync;
import com.alejandro.synclife.ui.SmokeRunner;
import com.alejandro.synclife.ui.View;
import com.alejandro.synclife.ui.control.TaskEditorDialog;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Button;
import javafx.stage.Window;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Comprueba cada 30 segundos si hay recordatorios vencidos y los muestra: un aviso fijo dentro de la
 * ventana con «Abrir», «Posponer 10 min» y «Hecha» y, si la ventana no está a la vista, también una
 * notificación del sistema. Sigue funcionando con la ventana oculta en la bandeja.
 */
public class ReminderPoller {

    private static final Logger LOG = Logger.getLogger(ReminderPoller.class.getName());
    private static final Duration INTERVAL = Duration.seconds(30);
    private static final java.time.Duration SNOOZE = java.time.Duration.ofMinutes(10);

    private final AppContext context;
    private final Timeline timeline = new Timeline(new KeyFrame(INTERVAL, e -> checkNow()));
    private boolean checking;

    public ReminderPoller(AppContext context) {
        this.context = context;
        timeline.setCycleCount(Animation.INDEFINITE);
    }

    /** Comprueba ya (al arrancar, para los avisos pendientes) y después cada 30 s. */
    public void start() {
        checkNow();
        timeline.play();
    }

    public void stop() {
        timeline.stop();
    }

    public void checkNow() {
        if (checking) {
            return;
        }
        checking = true;
        FxAsync.run(() -> context.reminders().claimDue(), due -> {
            checking = false;
            show(due);
        }, error -> {
            checking = false;
            LOG.log(Level.WARNING, "No se pudieron comprobar los recordatorios", error);
        });
    }

    private void show(Due due) {
        if (due.isEmpty() || !AppUi.isInstalled()) {
            return;
        }
        due.fresh().forEach(this::showReminder);
        if (!due.missed().isEmpty()) {
            showMissed(due.missed());
        }
        refreshTaskViews();
        beep();
    }

    /** Las listas muestran una campana con la hora del aviso: tras entregarlo o posponerlo hay que repintarlas. */
    private static void refreshTaskViews() {
        View current = AppUi.navigator().getCurrent();
        if (current != null && current != View.REVISION && current != View.AJUSTES) {
            AppUi.navigator().refreshCurrent();
        }
    }

    private void showReminder(Task task) {
        String when = task.getStartTime() == null ? "" : " · " + Formats.time(task.getStartTime());
        Button open = new Button("Abrir", new FontIcon("mdi2p-pencil-outline"));
        open.setOnAction(e -> openEditor(task));
        Button snooze = new Button("+10 min", new FontIcon("mdi2a-alarm-snooze"));
        snooze.setOnAction(e -> FxAsync.run(() -> context.reminders().snooze(task.getId(), SNOOZE), snoozed -> {
            AppUi.notifications().info("Te lo vuelvo a recordar a las "
                    + Formats.time(snoozed.getRemindAt().toLocalTime()) + ".");
            refreshTaskViews();
        }));
        Button done = new Button("Hecha", new FontIcon("mdi2c-check"));
        done.setOnAction(e -> FxAsync.run(() -> context.tasks().setStatus(task.getId(), TaskStatus.COMPLETADA),
                completed -> AppUi.navigator().refreshCurrent()));
        // Fijo hasta que se cierre: un recordatorio no debe pasar desapercibido
        AppUi.notifications().show(NotificationCenter.Kind.INFO, "mdi2b-bell-ring-outline",
                "Recordatorio" + when + "\n«" + task.getTitle() + "»", null, open, snooze, done);
        if (AppUi.tray().isWindowAway()) {
            AppUi.tray().notifySystem("Recordatorio" + when, task.getTitle());
        }
    }

    private void showMissed(List<Task> missed) {
        String titles = missed.stream().limit(3).map(t -> "«" + t.getTitle() + "»").collect(Collectors.joining(", "))
                + (missed.size() > 3 ? " y " + (missed.size() - 3) + " más" : "");
        Button today = new Button("Ver Hoy", new FontIcon("mdi2c-calendar-today"));
        today.setOnAction(e -> {
            AppUi.tray().showWindow();
            AppUi.navigator().navigate(View.HOY);
        });
        String message = (missed.size() == 1 ? "Pasó un recordatorio" : "Pasaron " + missed.size() + " recordatorios")
                + " mientras SyncLife estaba cerrado:\n" + titles;
        AppUi.notifications().show(NotificationCenter.Kind.WARNING, "mdi2b-bell-alert-outline", message,
                Duration.seconds(30), today);
        if (AppUi.tray().isWindowAway()) {
            AppUi.tray().notifySystem("Recordatorios pasados", titles);
        }
    }

    private void openEditor(Task task) {
        AppUi.tray().showWindow();
        Window owner = Dialogs.focusedWindow().orElse(null);
        TaskEditorDialog.open(owner, context, task, task.getScheduledDate(), AppUi.navigator()::refreshCurrent);
    }

    private void beep() {
        if (!context.settings().getNotifications().sound() || SmokeRunner.enabled()) {
            return;
        }
        try {
            java.awt.Toolkit.getDefaultToolkit().beep();
        } catch (Throwable e) {
            LOG.log(Level.FINE, "Sin sonido disponible", e);
        }
    }
}
