package com.alejandro.synclife.controller;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.SyncLifeApp;
import com.alejandro.synclife.config.AppSettings;
import com.alejandro.synclife.config.AppSettings.NotificationSettings;
import com.alejandro.synclife.config.AppSettings.PomodoroSettings;
import com.alejandro.synclife.config.AppSettings.ThemeMode;
import com.alejandro.synclife.model.TaskFilter;
import com.alejandro.synclife.persistence.DatabaseBackup;
import com.alejandro.synclife.service.DataResetService;
import com.alejandro.synclife.service.DemoData;
import com.alejandro.synclife.ui.AppUi;
import com.alejandro.synclife.ui.Choice;
import com.alejandro.synclife.ui.CsvTransfer;
import com.alejandro.synclife.ui.ReminderOptions;
import com.alejandro.synclife.ui.Ui;
import com.alejandro.synclife.ui.tray.TrayIntegration;
import com.alejandro.synclife.ui.Dialogs;
import com.alejandro.synclife.ui.FxAsync;
import com.alejandro.synclife.ui.Theme;
import com.alejandro.synclife.ui.ViewController;
import com.alejandro.synclife.ui.notify.NotificationCenter;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.ToggleGroup;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/** Pantalla "Ajustes": tema, rutinas, Pomodoro, notificaciones, copia de seguridad, CSV y datos de ejemplo. */
public class SettingsController implements ViewController {

    private final AppContext context;
    private final ToggleGroup themeGroup = new ToggleGroup();

    @FXML private RadioButton lightRadio;
    @FXML private RadioButton darkRadio;
    @FXML private Spinner<Integer> horizonSpinner;
    @FXML private Spinner<Integer> focusSpinner;
    @FXML private Spinner<Integer> shortBreakSpinner;
    @FXML private Spinner<Integer> longBreakSpinner;
    @FXML private Spinner<Integer> everySpinner;
    @FXML private CheckBox autoBreakCheck;
    @FXML private CheckBox soundCheck;
    @FXML private CheckBox trayCheck;
    @FXML private Label trayHint;
    @FXML private CheckBox systemNotificationsCheck;
    @FXML private CheckBox reminderSoundCheck;
    @FXML private ComboBox<Choice<Integer>> defaultReminderCombo;
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
        buildPomodoroSettings(settings.getPomodoro());
        buildNotificationSettings(settings.getNotifications());
        dataDirLabel.setText(context.dataDir().toString());

        String version = SyncLifeApp.class.getPackage().getImplementationVersion();
        versionLabel.setText("SyncLife " + (version == null ? "(desarrollo)" : version));
        runtimeLabel.setText("Java " + System.getProperty("java.version")
                + " · JavaFX " + System.getProperty("javafx.runtime.version", "?")
                + " · " + System.getProperty("os.name"));
    }

    /** Rangos iguales a los de {@link PomodoroSettings#clamped()}; cada cambio se guarda y se aplica al momento. */
    private void buildPomodoroSettings(PomodoroSettings pomodoro) {
        focusSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 90, pomodoro.focusMinutes(), 5));
        shortBreakSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 30, pomodoro.shortBreakMinutes()));
        longBreakSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 60, pomodoro.longBreakMinutes(), 5));
        everySpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(2, 8, pomodoro.longBreakEvery()));
        autoBreakCheck.setSelected(pomodoro.autoStartBreaks());
        soundCheck.setSelected(pomodoro.sound());
        for (Spinner<Integer> spinner : List.of(focusSpinner, shortBreakSpinner, longBreakSpinner, everySpinner)) {
            spinner.valueProperty().addListener((obs, old, value) -> savePomodoro());
        }
        autoBreakCheck.selectedProperty().addListener((obs, old, value) -> savePomodoro());
        soundCheck.selectedProperty().addListener((obs, old, value) -> savePomodoro());
    }

    private void savePomodoro() {
        context.settings().setPomodoro(new PomodoroSettings(focusSpinner.getValue(), shortBreakSpinner.getValue(),
                longBreakSpinner.getValue(), everySpinner.getValue(), autoBreakCheck.isSelected(),
                soundCheck.isSelected()));
        saveSettings();
        if (AppUi.isInstalled()) {
            AppUi.pomodoro().reloadSettings();
        }
    }

    private void buildNotificationSettings(NotificationSettings notifications) {
        boolean traySupported = TrayIntegration.isSupported();
        trayCheck.setSelected(notifications.minimizeToTray());
        trayCheck.setDisable(!traySupported);
        trayHint.setText(traySupported
                ? "Así los recordatorios y el pomodoro siguen funcionando con la ventana cerrada. Para salir del todo: «Salir» en el icono de la bandeja."
                : "Este escritorio no tiene bandeja del sistema: al cerrar la ventana se sale de SyncLife.");
        systemNotificationsCheck.setSelected(notifications.systemNotifications());
        systemNotificationsCheck.setDisable(!traySupported);
        reminderSoundCheck.setSelected(notifications.sound());
        defaultReminderCombo.getItems().setAll(ReminderOptions.forRoutines());
        Ui.selectChoice(defaultReminderCombo, notifications.defaultReminderMinutes());
        trayCheck.selectedProperty().addListener((obs, old, value) -> {
            saveNotifications();
            if (AppUi.isInstalled()) {
                if (value) {
                    AppUi.tray().install();
                } else {
                    AppUi.tray().uninstall();
                }
            }
        });
        systemNotificationsCheck.selectedProperty().addListener((obs, old, value) -> saveNotifications());
        reminderSoundCheck.selectedProperty().addListener((obs, old, value) -> saveNotifications());
        defaultReminderCombo.valueProperty().addListener((obs, old, value) -> saveNotifications());
    }

    private void saveNotifications() {
        context.settings().setNotifications(new NotificationSettings(trayCheck.isSelected(),
                systemNotificationsCheck.isSelected(), reminderSoundCheck.isSelected(),
                Ui.choiceValue(defaultReminderCombo)));
        saveSettings();
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
        File file = chooser.showSaveDialog(window());
        if (file == null) {
            return;
        }
        Path target = file.toPath();
        FxAsync.runVoid(() -> DatabaseBackup.backupTo(context.jdbcUrl(), target),
                () -> Dialogs.info("Copia de seguridad creada", target.toString()));
    }

    @FXML
    private void exportCsv() {
        CsvTransfer.exportTasks(window(), context, "synclife-tareas-" + context.today() + ".csv",
                () -> context.tasks().search(TaskFilter.all()));
    }

    @FXML
    private void importCsv() {
        CsvTransfer.importTasks(window(), context, () -> {
            if (AppUi.isInstalled()) {
                AppUi.navigator().refreshCurrent();
            }
        });
    }

    private Window window() {
        return dataDirLabel.getScene().getWindow();
    }

    @FXML
    private void resetData() {
        FxAsync.run(() -> context.dataReset().summary(), summary -> {
            if (!Dialogs.confirmTyped("¿Borrar todos los datos?", resetMessage(summary), "BORRAR", "Borrar todo")) {
                return;
            }
            if (AppUi.isInstalled()) {
                if (AppUi.pomodoro().isActive()) {
                    AppUi.pomodoro().stop(); // su tiempo se guarda antes de borrar (mismo hilo de BD) y se va con todo
                }
                AppUi.pomodoro().dismiss(); // la tarjeta no puede quedarse con una tarea que ya no existe
            }
            FxAsync.run(() -> context.dataReset().resetAll(), backup -> {
                if (AppUi.isInstalled()) {
                    AppUi.navigator().reset(); // ninguna pantalla debe conservar tareas que ya no existen
                    Button open = new Button("Abrir carpeta", new FontIcon("mdi2f-folder-open-outline"));
                    open.setOnAction(e -> SyncLifeApp.hostServices().showDocument(backup.getParent().toUri().toString()));
                    AppUi.notifications().show(NotificationCenter.Kind.SUCCESS, "mdi2c-check-circle-outline",
                            "Datos borrados: SyncLife empieza de cero.\nTienes una copia de lo anterior en la carpeta "
                                    + "«copias».", Duration.seconds(10), open);
                }
            });
        });
    }

    /** Texto del diálogo de confirmación de «Borrar todos los datos». */
    public static String resetMessage(DataResetService.Summary summary) {
        String content = summary.isEmpty() ? "No hay tareas, rutinas, objetivos ni revisiones."
                : "Se borrarán " + plural(summary.tasks(), "tarea", "tareas") + " (también las de rutinas), "
                + plural(summary.routines(), "rutina", "rutinas") + ", "
                + plural(summary.goals(), "objetivo", "objetivos") + " y "
                + plural(summary.reviews(), "revisión semanal", "revisiones semanales") + ".";
        return content + " Tus " + plural(summary.categories(), "categoría", "categorías")
                + " se sustituyen por las de serie.\n\nAntes se guarda una copia de todo en la carpeta «copias» de "
                + "la carpeta de datos. Los ajustes (tema, pomodoro, notificaciones) se conservan.";
    }

    private static String plural(long count, String one, String many) {
        return count + " " + (count == 1 ? one : many);
    }

    @FXML
    private void loadDemo() {
        if (Dialogs.confirm("¿Cargar datos de ejemplo?",
                "Se añadirán algunas tareas y rutinas de muestra a las que ya tengas.", "Cargar")) {
            FxAsync.runVoid(() -> DemoData.seed(context),
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
