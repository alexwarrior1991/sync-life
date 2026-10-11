package com.alejandro.synclife.ui.notify;

import atlantafx.base.controls.Notification;
import atlantafx.base.theme.Styles;
import atlantafx.base.util.Animations;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.scene.control.Button;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.logging.Logger;

/**
 * Avisos dentro de la ventana (arriba a la derecha) que desaparecen solos. Sustituyen a los diálogos
 * para lo que no requiere respuesta: "Pomodoro terminado", "Tarea clasificada"…
 */
public class NotificationCenter {

    public enum Kind { INFO, SUCCESS, WARNING }

    private static final Logger LOG = Logger.getLogger(NotificationCenter.class.getName());
    private static final int MAX_VISIBLE = 4;
    private static final Duration DEFAULT_DURATION = Duration.seconds(6);

    private final VBox layer;
    private int shown;

    /** {@code layer} es la capa transparente sobre la ventana donde se apilan los avisos. */
    public NotificationCenter(VBox layer) {
        this.layer = layer;
        layer.setPickOnBounds(false);
    }

    public void info(String message) {
        show(Kind.INFO, "mdi2i-information-outline", message, DEFAULT_DURATION);
    }

    public void success(String message) {
        show(Kind.SUCCESS, "mdi2c-check-circle-outline", message, DEFAULT_DURATION);
    }

    public void warning(String message) {
        show(Kind.WARNING, "mdi2a-alert-circle-outline", message, DEFAULT_DURATION);
    }

    /**
     * Muestra un aviso. Con {@code duration == null} se queda hasta que se cierre.
     * Al pasar el ratón por encima no desaparece.
     */
    public Notification show(Kind kind, String iconLiteral, String message, Duration duration, Button... actions) {
        LOG.info("Aviso: " + message.replace('\n', ' '));
        shown++;
        Notification notification = new Notification(message, new FontIcon(iconLiteral));
        notification.getStyleClass().addAll(Styles.ELEVATED_2, "toast", switch (kind) {
            case INFO -> Styles.ACCENT;
            case SUCCESS -> Styles.SUCCESS;
            case WARNING -> Styles.WARNING;
        });
        double width = actions.length > 0 ? 420 : 360; // con botones, que quepan sin cortarse
        notification.setPrefWidth(width);
        notification.setMaxWidth(width);
        notification.setMaxHeight(Region.USE_PREF_SIZE);
        if (actions.length > 0) {
            notification.setPrimaryActions(actions);
            for (Button action : actions) {
                action.addEventHandler(javafx.event.ActionEvent.ACTION, e -> dismiss(notification));
            }
        }
        notification.setOnClose(e -> dismiss(notification));

        while (layer.getChildren().size() >= MAX_VISIBLE) {
            layer.getChildren().remove(0);
        }
        layer.getChildren().add(notification);
        Animations.fadeInDown(notification, Duration.millis(250)).playFromStart();

        if (duration != null) {
            PauseTransition timer = new PauseTransition(duration);
            timer.setOnFinished(e -> dismiss(notification));
            notification.setOnMouseEntered(e -> timer.stop());
            notification.setOnMouseExited(e -> timer.playFromStart());
            timer.play();
        }
        return notification;
    }

    /** Avisos mostrados desde que arrancó la aplicación (para la prueba de humo). */
    public int shownCount() {
        return shown;
    }

    public int visibleCount() {
        return layer.getChildren().size();
    }

    private void dismiss(Notification notification) {
        if (!layer.getChildren().contains(notification)) {
            return;
        }
        Timeline out = Animations.fadeOut(notification, Duration.millis(200));
        out.setOnFinished(e -> layer.getChildren().remove(notification));
        out.playFromStart();
    }
}
