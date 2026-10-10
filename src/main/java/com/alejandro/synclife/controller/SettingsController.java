package com.alejandro.synclife.controller;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.SyncLifeApp;
import com.alejandro.synclife.config.AppSettings;
import com.alejandro.synclife.config.AppSettings.ThemeMode;
import com.alejandro.synclife.persistence.DatabaseBackup;
import com.alejandro.synclife.service.DemoData;
import com.alejandro.synclife.ui.Dialogs;
import com.alejandro.synclife.ui.FxAsync;
import com.alejandro.synclife.ui.Theme;
import com.alejandro.synclife.ui.ViewController;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.ToggleGroup;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;

/** Pantalla "Ajustes": tema, horizonte de rutinas, copia de seguridad y datos de ejemplo. */
public class SettingsController implements ViewController {

    private final AppContext context;
    private final ToggleGroup themeGroup = new ToggleGroup();

    @FXML private RadioButton lightRadio;
    @FXML private RadioButton darkRadio;
    @FXML private Spinner<Integer> horizonSpinner;
    @FXML private Label dataDirLabel;
    @FXML private Label versionLabel;
    @FXML private Label runtimeLabel;

    public SettingsController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        AppSettings settings = context.settings();
        lightRadio.setToggleGroup(themeGroup);
        darkRadio.setToggleGroup(themeGroup);
        (settings.getTheme() == ThemeMode.OSCURO ? darkRadio : lightRadio).setSelected(true);
        themeGroup.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                themeGroup.selectToggle(old);
                return;
            }
            ThemeMode mode = selected == darkRadio ? ThemeMode.OSCURO : ThemeMode.CLARO;
            Theme.apply(mode);
            settings.setTheme(mode);
            saveSettings();
        });

        horizonSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                AppSettings.MIN_HORIZON, AppSettings.MAX_HORIZON, settings.getRoutineHorizonDays()));
        dataDirLabel.setText(context.dataDir().toString());

        String version = SyncLifeApp.class.getPackage().getImplementationVersion();
        versionLabel.setText("SyncLife " + (version == null ? "(desarrollo)" : version));
        runtimeLabel.setText("Java " + System.getProperty("java.version")
                + " · JavaFX " + System.getProperty("javafx.runtime.version", "?")
                + " · " + System.getProperty("os.name"));
    }

    @Override
    public void onShow() {
        horizonSpinner.getValueFactory().setValue(context.settings().getRoutineHorizonDays());
    }

    @FXML
    private void applyHorizon() {
        try {
            horizonSpinner.getValueFactory().setValue(Integer.parseInt(horizonSpinner.getEditor().getText().trim()));
        } catch (NumberFormatException ignored) {
            // se queda con el último valor válido
        }
        context.settings().setRoutineHorizonDays(horizonSpinner.getValue());
        saveSettings();
        FxAsync.run(() -> context.routines().ensureHorizon(), created ->
                Dialogs.info("Horizonte actualizado",
                        created == 0 ? "No hacía falta generar tareas nuevas."
                                : "Se han generado " + created + " tareas de rutinas."));
    }

    @FXML
    private void openDataDir() {
        SyncLifeApp.hostServices().showDocument(context.dataDir().toUri().toString());
    }

    @FXML
    private void backup() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar copia de seguridad");
        chooser.setInitialFileName("synclife-" + context.today() + ".db");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Base de datos SQLite", "*.db"));
        File file = chooser.showSaveDialog(dataDirLabel.getScene().getWindow());
        if (file == null) {
            return;
        }
        Path target = file.toPath();
        FxAsync.runVoid(() -> DatabaseBackup.backupTo(context.jdbcUrl(), target),
                () -> Dialogs.info("Copia de seguridad creada", target.toString()));
    }

    @FXML
    private void loadDemo() {
        if (Dialogs.confirm("¿Cargar datos de ejemplo?",
                "Se añadirán algunas tareas y rutinas de muestra a las que ya tengas.", "Cargar")) {
            FxAsync.runVoid(() -> DemoData.seed(context.tasks(), context.categories(), context.routines()),
                    () -> Dialogs.info("Datos de ejemplo cargados", "Échales un vistazo en «Hoy» y «Semana»."));
        }
    }

    private void saveSettings() {
        try {
            context.settings().save();
        } catch (Exception e) {
            Dialogs.error(e);
        }
    }
}
