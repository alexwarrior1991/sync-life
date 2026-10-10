package com.alejandro.synclife;

import com.alejandro.synclife.config.AppPaths;
import com.alejandro.synclife.config.LoggingConfig;
import com.alejandro.synclife.ui.SmokeRunner;
import javafx.application.Application;

import java.util.Locale;

/**
 * Punto de entrada del JAR. No extiende {@link Application} a propósito: así JavaFX puede
 * arrancar desde el classpath de un JAR ejecutable sin módulos.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        // Español de España: textos de diálogos y DatePicker en español y semanas que empiezan en lunes
        Locale.setDefault(Locale.of("es", "ES"));
        LoggingConfig.init(AppPaths.dataDir());
        Application.launch(SyncLifeApp.class, args);
        if (SmokeRunner.enabled()) {
            System.exit(SmokeRunner.exitCode());
        }
    }
}
