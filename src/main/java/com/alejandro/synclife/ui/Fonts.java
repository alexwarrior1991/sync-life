package com.alejandro.synclife.ui;

import javafx.scene.text.Font;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registra la fuente JetBrains Mono (la del editor de IntelliJ) incluida en el JAR.
 * Se usa en toda la interfaz mediante {@code .root} en {@code app.css}.
 * Licencia: SIL Open Font License 1.1 (ver {@code fonts/OFL.txt}).
 */
public final class Fonts {

    public static final String FAMILY = "JetBrains Mono";

    private static final Logger LOG = Logger.getLogger(Fonts.class.getName());
    private static final String[] FACES = {"Regular", "Italic", "SemiBold", "Bold", "BoldItalic"};

    private Fonts() {
    }

    /** Carga todas las variantes. Si alguna falla, la app sigue con la fuente del sistema. */
    public static void load() {
        for (String face : FACES) {
            String path = "/com/alejandro/synclife/fonts/JetBrainsMono-" + face + ".ttf";
            try (InputStream in = Fonts.class.getResourceAsStream(path)) {
                Font font = in == null ? null : Font.loadFont(in, 13);
                if (font == null) {
                    LOG.warning("No se pudo cargar la fuente " + path);
                } else {
                    LOG.fine("Fuente cargada: " + font.getName());
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "No se pudo leer la fuente " + path, e);
            }
        }
    }
}
