package com.alejandro.synclife.ui;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import com.alejandro.synclife.config.AppSettings.ThemeMode;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;

/** Tema AtlantaFX (Primer claro/oscuro) + la hoja de estilos propia de la app. */
public final class Theme {

    private static final String APP_CSS = Theme.class
            .getResource("/com/alejandro/synclife/css/app.css").toExternalForm();

    private Theme() {
    }

    /** Cambia el tema de toda la aplicación al instante (también ventanas ya abiertas). */
    public static void apply(ThemeMode mode) {
        String stylesheet = mode == ThemeMode.OSCURO
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet();
        Application.setUserAgentStylesheet(stylesheet);
    }

    public static void install(Scene scene) {
        if (!scene.getStylesheets().contains(APP_CSS)) {
            scene.getStylesheets().add(APP_CSS);
        }
    }

    /** Para diálogos, que tienen su propia escena. */
    public static void style(Parent root) {
        if (!root.getStylesheets().contains(APP_CSS)) {
            root.getStylesheets().add(APP_CSS);
        }
    }
}
