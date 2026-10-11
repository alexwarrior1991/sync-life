package com.alejandro.synclife.ui;

import atlantafx.base.theme.Styles;
import com.alejandro.synclife.service.ValidationException;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Diálogos estándar en español. En modo "sin cabeza" (prueba de humo) solo registran. */
public final class Dialogs {

    private static final Logger LOG = Logger.getLogger(Dialogs.class.getName());
    private static final AtomicInteger ERRORS = new AtomicInteger();
    private static volatile boolean headless;

    private Dialogs() {
    }

    public static void setHeadless(boolean value) {
        headless = value;
    }

    public static int errorCount() {
        return ERRORS.get();
    }

    public static void error(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        if (cause instanceof ValidationException) {
            LOG.info("Validación: " + cause.getMessage());
            if (headless) {
                ERRORS.incrementAndGet();
                return;
            }
            Alert alert = alert(Alert.AlertType.WARNING, "Revisa los datos", cause.getMessage());
            alert.showAndWait();
            return;
        }
        LOG.log(Level.SEVERE, "Error inesperado", cause);
        ERRORS.incrementAndGet();
        if (headless) {
            return;
        }
        String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        Alert alert = alert(Alert.AlertType.ERROR, "Ha ocurrido un error inesperado", message);
        TextArea details = new TextArea(stackTrace(cause));
        details.setEditable(false);
        details.setWrapText(false);
        details.setPrefRowCount(12);
        alert.getDialogPane().setExpandableContent(details);
        alert.showAndWait();
    }

    public static void info(String header, String content) {
        if (headless) {
            LOG.info(header + ": " + content);
            return;
        }
        alert(Alert.AlertType.INFORMATION, header, content).showAndWait();
    }

    /** Pide confirmación con un botón de acción explícito (p. ej. "Eliminar"). */
    public static boolean confirm(String header, String content, String actionLabel) {
        if (headless) {
            return true;
        }
        Alert alert = alert(Alert.AlertType.CONFIRMATION, header, content);
        ButtonType action = new ButtonType(actionLabel, ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(action, ButtonType.CANCEL);
        Optional<ButtonType> answer = alert.showAndWait();
        return answer.isPresent() && answer.get() == action;
    }

    /** Para lo que no tiene vuelta atrás: el botón solo se activa tras escribir {@code word} (p. ej. BORRAR). */
    public static boolean confirmTyped(String header, String content, String word, String actionLabel) {
        if (headless) {
            return true;
        }
        return typedConfirmation(header, content, word, actionLabel).showAndWait()
                .map(button -> button.getButtonData() == ButtonBar.ButtonData.OK_DONE).orElse(false);
    }

    public static Alert typedConfirmation(String header, String content, String word, String actionLabel) {
        Alert alert = alert(Alert.AlertType.WARNING, header, null);
        Label message = new Label(content);
        message.setWrapText(true);
        TextField field = new TextField();
        field.setPromptText(word);
        VBox box = new VBox(12, message, new Label("Para confirmar, escribe " + word + ":"), field);
        box.setPrefWidth(480);
        alert.getDialogPane().setContent(box);
        ButtonType action = new ButtonType(actionLabel, ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(action, ButtonType.CANCEL);
        Button button = (Button) alert.getDialogPane().lookupButton(action);
        button.getStyleClass().add(Styles.DANGER);
        button.disableProperty().bind(Bindings.createBooleanBinding(
                () -> !word.equalsIgnoreCase(field.getText().trim()), field.textProperty()));
        alert.setOnShown(e -> field.requestFocus());
        return alert;
    }

    private static Alert alert(Alert.AlertType type, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle("SyncLife");
        alert.setHeaderText(header);
        alert.setContentText(content);
        focusedWindow().ifPresent(alert::initOwner);
        Theme.style(alert.getDialogPane());
        return alert;
    }

    public static Optional<Window> focusedWindow() {
        return Window.getWindows().stream().filter(Window::isFocused).findFirst()
                .or(() -> Window.getWindows().stream().filter(Window::isShowing).findFirst());
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof ExecutionException || current instanceof CompletionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
