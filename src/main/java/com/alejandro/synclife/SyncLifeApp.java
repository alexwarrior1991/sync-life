package com.alejandro.synclife;

import com.alejandro.synclife.config.AppPaths;
import com.alejandro.synclife.controller.MainController;
import com.alejandro.synclife.ui.Dialogs;
import com.alejandro.synclife.ui.Fonts;
import com.alejandro.synclife.ui.FxAsync;
import com.alejandro.synclife.ui.SmokeRunner;
import com.alejandro.synclife.ui.Theme;
import com.alejandro.synclife.ui.View;
import com.alejandro.synclife.ui.ViewLoader;
import javafx.application.Application;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;
import java.time.Clock;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Aplicación JavaFX: arranca la base de datos en {@link #init()} y muestra la ventana principal. */
public class SyncLifeApp extends Application {

    private static final Logger LOG = Logger.getLogger(SyncLifeApp.class.getName());
    private static HostServices hostServices;

    private AppContext context;
    private Throwable bootError;

    public static HostServices hostServices() {
        return hostServices;
    }

    /** Se ejecuta fuera del hilo de JavaFX: migraciones, Hibernate y generación de rutinas. */
    @Override
    public void init() {
        try {
            context = AppContext.bootstrap(AppPaths.dataDir(), Clock.systemDefaultZone());
        } catch (Throwable e) {
            LOG.log(Level.SEVERE, "No se pudo iniciar SyncLife", e);
            bootError = e;
        }
    }

    @Override
    public void start(Stage stage) {
        hostServices = getHostServices();
        installUncaughtExceptionHandler();
        if (SmokeRunner.enabled()) {
            Dialogs.setHeadless(true);
        }
        if (bootError != null) {
            showBootError();
            return;
        }

        Fonts.load();
        Theme.apply(context.settings().getTheme());
        ViewLoader.Loaded<MainController> main = ViewLoader.load(context, "main.fxml");
        Scene scene = new Scene(main.root(), 1280, 820);
        Theme.install(scene);

        stage.setTitle("SyncLife");
        stage.setMinWidth(1000);
        stage.setMinHeight(640);
        stage.setScene(scene);
        for (int size : new int[]{16, 32, 64, 256}) {
            InputStream icon = SyncLifeApp.class.getResourceAsStream("/com/alejandro/synclife/images/icon-" + size + ".png");
            if (icon != null) {
                stage.getIcons().add(new Image(icon));
            }
        }
        main.controller().start(scene, initialView());
        stage.show();

        if (SmokeRunner.enabled()) {
            new SmokeRunner(context, main.controller(), stage).start();
        }
    }

    @Override
    public void stop() {
        FxAsync.shutdown();
        if (context != null) {
            context.close();
        }
    }

    private static View initialView() {
        String name = System.getProperty("synclife.startView");
        if (name != null) {
            try {
                return View.valueOf(name.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                LOG.warning("Vista inicial desconocida: " + name);
            }
        }
        return View.HOY;
    }

    private void installUncaughtExceptionHandler() {
        Thread.UncaughtExceptionHandler handler = (thread, error) -> {
            LOG.log(Level.SEVERE, "Error no controlado en " + thread.getName(), error);
            if (Platform.isFxApplicationThread()) {
                Dialogs.error(error);
            } else {
                Platform.runLater(() -> Dialogs.error(error));
            }
        };
        Thread.setDefaultUncaughtExceptionHandler(handler);
        Thread.currentThread().setUncaughtExceptionHandler(handler);
    }

    private void showBootError() {
        if (SmokeRunner.enabled()) {
            SmokeRunner.fail("Error de arranque: " + bootError);
            Platform.exit();
            return;
        }
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("SyncLife");
        alert.setHeaderText("No se pudo abrir la base de datos de SyncLife");
        alert.setContentText(bootError.getMessage() + "\n\nCarpeta de datos: " + AppPaths.dataDir()
                + "\nConsulta el log en " + AppPaths.logsDir(AppPaths.dataDir()));
        alert.showAndWait();
        Platform.exit();
    }
}
