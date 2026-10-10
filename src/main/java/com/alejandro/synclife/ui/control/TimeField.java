package com.alejandro.synclife.ui.control;

import com.alejandro.synclife.ui.Formats;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

import java.time.LocalTime;
import java.util.Optional;

/**
 * Campo de hora tolerante: acepta "9", "930", "9:30" o "09:30". Vacío = sin hora.
 * Si el texto no es una hora válida se marca en rojo y {@link #validProperty()} pasa a false.
 */
public class TimeField extends TextField {

    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");

    private final ObjectProperty<LocalTime> time = new SimpleObjectProperty<>(this, "time");
    private final ReadOnlyBooleanWrapper valid = new ReadOnlyBooleanWrapper(this, "valid", true);

    public TimeField() {
        getStyleClass().add("time-field");
        setPromptText("hh:mm");
        setPrefColumnCount(5);
        setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().matches("[0-9:]{0,5}") ? change : null));
        textProperty().addListener((obs, old, text) -> {
            Optional<LocalTime> parsed = parse(text);
            boolean ok = text == null || text.isBlank() || parsed.isPresent();
            valid.set(ok);
            time.set(parsed.orElse(null));
            pseudoClassStateChanged(INVALID, !ok);
        });
        focusedProperty().addListener((obs, was, focused) -> {
            if (!focused && time.get() != null) {
                setText(Formats.time(time.get())); // normaliza "930" → "09:30"
            }
        });
    }

    public static Optional<LocalTime> parse(String text) {
        return Formats.parseTime(text);
    }

    public LocalTime getTime() {
        return time.get();
    }

    public void setTime(LocalTime value) {
        setText(value == null ? "" : Formats.time(value));
    }

    public ObjectProperty<LocalTime> timeProperty() {
        return time;
    }

    public boolean isValid() {
        return valid.get();
    }

    public ReadOnlyBooleanProperty validProperty() {
        return valid.getReadOnlyProperty();
    }
}
