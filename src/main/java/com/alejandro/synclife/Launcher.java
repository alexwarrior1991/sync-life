package com.alejandro.synclife;

import com.alejandro.synclife.config.AppPaths;
import com.alejandro.synclife.config.LoggingConfig;
import com.alejandro.synclife.config.SingleInstance;
import com.alejandro.synclife.ui.AppUi;
import com.alejandro.synclife.ui.SmokeRunner;
import javafx.application.Application;
import javafx.application.Platform;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

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
        Path dataDir = AppPaths.dataDir();
        LoggingConfig.init(dataDir);
        // Una sola SyncLife por carpeta de datos: si ya está abierta (quizá en la bandeja), se trae al frente
        Optional<SingleInstance> instance = SingleInstance.acquire(dataDir,
                () -> Platform.runLater(AppUi::showMainWindow));
        if (instance.isEmpty()) {
            System.out.println("SyncLife ya está abierta: se muestra su ventana.");
            System.exit(0);
            return;
        }
        try (SingleInstance ignored = instance.get()) {
            Application.launch(SyncLifeApp.class, args);
        }
        // AWT (bandeja, sonido) deja hilos vivos: se termina el proceso explícitamente
        System.exit(SmokeRunner.enabled() ? SmokeRunner.exitCode() : 0);
    }
}
