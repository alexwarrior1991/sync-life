package com.alejandro.synclife.ui;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.config.AppPaths;
import com.alejandro.synclife.config.AppSettings.ThemeMode;
import com.alejandro.synclife.controller.MainController;
import com.alejandro.synclife.controller.ReviewController;
import com.alejandro.synclife.controller.SettingsController;
import com.alejandro.synclife.controller.WeekController;
import com.alejandro.synclife.model.entity.Goal;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.Quadrant;
import com.alejandro.synclife.model.enums.TaskStatus;
import com.alejandro.synclife.service.DeleteResult;
import com.alejandro.synclife.service.DemoData;
import com.alejandro.synclife.service.ReminderService;
import com.alejandro.synclife.service.TaskCsvService.ImportOptions;
import com.alejandro.synclife.service.TaskCsvService.ImportResult;
import com.alejandro.synclife.service.TaskCsvService.Preview;
import com.alejandro.synclife.ui.control.ChecklistEditor;
import com.alejandro.synclife.ui.control.CsvImportDialog;
import com.alejandro.synclife.ui.control.TaskEditorDialog;
import com.alejandro.synclife.ui.control.TaskEditorDialog.EditorResult;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Prueba de humo automática ({@code -Dsynclife.smoke=true}): carga datos de ejemplo, recorre todas las
 * pantallas, abre el editor, crea y completa una tarea y termina con código 0 si no hubo errores.
 * Con {@code -Dsynclife.smoke.shots=<carpeta>} guarda una captura PNG de cada pantalla.
 */
public final class SmokeRunner {

    private record ReminderCheck(ReminderService.Due due, Task task) {
    }

    private record CsvCheck(Preview preview, ImportResult result, Preview withProblems) {
    }

    private record EditData(Task task, TaskEditorDialog.EditorData editor) {
    }

    private static final Logger LOG = Logger.getLogger(SmokeRunner.class.getName());
    private static volatile int exitCode = 0;

    private final AppContext context;
    private final MainController main;
    private final Stage stage;
    private final Path shotsDir;
    private final Deque<Runnable> steps = new ArrayDeque<>();
    private Dialog<EditorResult> editor;
    private Dialog<ImportOptions> csvDialog;
    private javafx.scene.control.Alert resetDialog;
    private String originalTitle;
    private int originalChecklist;
    private long pomodoroTaskId;
    private long reminderTaskId;
    private int pomodoroMinutesBefore;

    public SmokeRunner(AppContext context, MainController main, Stage stage) {
        this.context = context;
        this.main = main;
        this.stage = stage;
        String shots = System.getProperty("synclife.smoke.shots");
        this.shotsDir = shots == null || shots.isBlank() ? null : Path.of(shots);
    }

    public static boolean enabled() {
        return Boolean.getBoolean("synclife.smoke");
    }

    public static int exitCode() {
        return exitCode;
    }

    public static void fail(String reason) {
        LOG.severe("SMOKE: " + reason);
        System.out.println("SMOKE FAILED: " + reason);
        exitCode = 1;
    }

    public void start() {
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(180_000);
                System.out.println("SMOKE FAILED: tiempo agotado");
                Runtime.getRuntime().halt(2);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "smoke-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();

        // El runtime empaquetado debe incluir los datos de idioma (jdk.localedata): fechas en español
        steps.add(() -> {
            String day = Formats.dayTitle(java.time.LocalDate.of(2026, 10, 12));
            if (!day.equals("Lunes, 12 de octubre")) {
                fail("las fechas no salen en español (¿falta jdk.localedata?): " + day);
            }
        });
        steps.add(() -> FxAsync.runVoid(() -> {
            if (context.routines().findAll().isEmpty()) {
                DemoData.seed(context);
            }
        }, null));
        for (View view : View.values()) {
            steps.add(() -> main.navigator().navigate(view));
            steps.add(() -> shot(view.name().toLowerCase()));
        }
        steps.add(() -> FxAsync.run(() -> TaskEditorDialog.load(context, null), data -> {
            editor = TaskEditorDialog.create(stage, context, null, context.today(), data);
            editor.show();
        }));
        steps.add(() -> shot("editor", editor.getDialogPane().getScene()));
        steps.add(() -> editor.close());
        // Editar una tarea existente con lista de comprobación: añadir un paso, "Guardar" y persistir
        steps.add(() -> FxAsync.run(() -> {
            Task existing = context.tasks().tasksForDay(context.today()).stream()
                    .filter(t -> !t.isRoutineInstance() && t.getSubtaskTotal() > 0).findFirst().orElseThrow();
            return new EditData(existing, TaskEditorDialog.load(context, existing));
        }, data -> {
            Task existing = data.task();
            originalTitle = existing.getTitle();
            originalChecklist = data.editor().checklist().size();
            editor = TaskEditorDialog.create(stage, context, existing, existing.getScheduledDate(), data.editor());
            editor.show();
        }));
        steps.add(() -> shot("editor-checklist", editor.getDialogPane().getScene()));
        steps.add(() -> {
            boolean hasDelete = editor.getDialogPane().getButtonTypes().stream()
                    .anyMatch(b -> b.getButtonData() == ButtonBar.ButtonData.LEFT);
            if (!hasDelete) {
                fail("el editor de una tarea existente no ofrece «Eliminar»");
            }
            if (!(editor.getDialogPane().lookup(".checklist-editor") instanceof ChecklistEditor checklist)
                    || checklist.size() != originalChecklist) {
                fail("el editor no muestra la lista de comprobación de la tarea");
                return;
            }
            checklist.add("Paso añadido en la prueba de humo");
            ButtonType save = editor.getDialogPane().getButtonTypes().stream()
                    .filter(b -> b.getButtonData() == ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();
            ((Button) editor.getDialogPane().lookupButton(save)).fire();
            EditorResult result = editor.getResult();
            if (result == null || result.delete()) {
                fail("el diálogo no devolvió la tarea editada");
                return;
            }
            FxAsync.run(() -> context.tasks().save(result.task(), result.checklist()), saved -> {
                if (!saved.getTitle().equals(originalTitle)) {
                    fail("el título cambió al guardar sin modificar");
                }
                if (saved.getSubtaskTotal() != originalChecklist + 1) {
                    fail("la lista de comprobación no se guardó: " + saved.getSubtaskTotal() + " pasos");
                }
            });
        });
        // Revisión semanal: recorrer los cinco pasos, escribir notas y guardar desde la interfaz
        steps.add(() -> main.navigator().navigate(View.REVISION));
        for (int i = 0; i < 5; i++) {
            int index = i;
            steps.add(() -> review().goToStep(index));
            steps.add(() -> shot("revision-" + (index + 1)));
        }
        steps.add(() -> {
            if (!(stage.getScene().lookup("#review-notes") instanceof TextArea notes)) {
                fail("no se encuentra el campo de notas de la revisión");
                return;
            }
            notes.setText("Notas de la prueba de humo");
            review().save();
        });
        steps.add(() -> FxAsync.run(() -> context.reviews().find(context.reviews().defaultPlannedMonday()), saved -> {
            if (saved.isEmpty() || !"Notas de la prueba de humo".equals(saved.get().getNotes())) {
                fail("la revisión semanal no se guardó desde el asistente");
            }
        }));
        // La semana planificada muestra sus prioridades
        steps.add(() -> {
            main.navigator().navigate(View.SEMANA);
            ((WeekController) main.navigator().controller(View.SEMANA)).showWeek(context.reviews().defaultPlannedMonday());
        });
        steps.add(() -> {
            if (stage.getScene().getRoot().lookupAll(".priority-chip").isEmpty()) {
                fail("la semana planificada no muestra las prioridades de la revisión");
            }
            shot("semana-prioridades");
        });
        steps.add(() -> ((WeekController) main.navigator().controller(View.SEMANA)).showWeek(context.today()));
        // Objetivos: el avance cuenta las tareas vinculadas completadas
        steps.add(() -> FxAsync.run(() -> {
            Goal goal = context.goals().save(new Goal("Objetivo de la prueba de humo", Area.VIDA));
            Task a = context.tasks().quickAdd("Paso 1 del objetivo", Area.VIDA, null, null);
            Task b = context.tasks().quickAdd("Paso 2 del objetivo", Area.VIDA, null, null);
            context.goals().link(goal.getId(), List.of(a.getId(), b.getId()));
            context.tasks().toggleDone(a.getId());
            var progress = context.goals().progress(goal.getId()).orElseThrow();
            context.goals().delete(goal.getId());
            context.tasks().deleteAll(List.of(a.getId(), b.getId()));
            return progress.done() == 1 && progress.total() == 2;
        }, ok -> {
            if (!ok) {
                fail("el avance del objetivo no cuenta bien sus tareas");
            }
        }));
        // Recordatorios: uno vencido ahora aparece como aviso fijo en la ventana
        steps.add(() -> FxAsync.run(() -> {
            Task task = new Task("Llamar al proveedor (recordatorio de prueba)", Area.TRABAJO);
            task.setScheduledDate(context.today());
            task.setRemindAt(java.time.LocalDateTime.now(context.clock()));
            return context.tasks().save(task);
        }, task -> {
            reminderTaskId = task.getId();
            main.navigator().navigate(View.HOY);
            main.reminders().checkNow();
        }));
        steps.add(() -> {
            if (reminderToast().isEmpty()) {
                fail("el recordatorio vencido no se mostró");
            }
            shot("notificacion");
        });
        // «Hecha» desde el propio aviso completa la tarea y lo cierra
        steps.add(() -> reminderToast().flatMap(toast -> toast.getPrimaryActions().stream()
                .filter(button -> "Hecha".equals(button.getText())).findFirst()).ifPresentOrElse(Button::fire,
                () -> fail("el recordatorio no ofrece «Hecha»")));
        steps.add(() -> FxAsync.run(() -> new ReminderCheck(context.reminders().claimDue(),
                context.tasks().findById(reminderTaskId).orElseThrow()), check -> {
            if (!check.due().isEmpty()) {
                fail("un recordatorio se entregó dos veces");
            }
            if (check.task().getStatus() != TaskStatus.COMPLETADA || reminderToast().isPresent()) {
                fail("«Hecha» en el recordatorio no completó la tarea o no cerró el aviso");
            }
            FxAsync.runVoid(() -> context.tasks().delete(reminderTaskId), null);
        }));
        // CSV: reimportar lo exportado con "omitir idénticas" no crea nada; vista previa con una fila mala y un aviso
        steps.add(() -> FxAsync.run(() -> {
            byte[] csv = context.csv().exportAll();
            Preview preview = context.csv().preview(csv);
            ImportResult result = context.csv().apply(preview, new ImportOptions(true, true));
            byte[] extra = "Revisar presupuesto;;Trabajo;;;Altísima\r\n;sin título\r\n"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] withProblems = java.util.Arrays.copyOf(csv, csv.length + extra.length);
            System.arraycopy(extra, 0, withProblems, csv.length, extra.length);
            return new CsvCheck(preview, result, context.csv().preview(withProblems));
        }, check -> {
            if (!check.preview().errors().isEmpty() || check.preview().rows().isEmpty()) {
                fail("el CSV exportado no se puede volver a leer: " + check.preview().errors());
            }
            if (check.result().created() != 0 || check.result().skippedIdentical() != check.preview().rows().size()) {
                fail("reimportar el CSV exportado duplicó tareas: " + check.result());
            }
            if (check.withProblems().errors().size() != 1 || check.withProblems().warnings().size() != 1) {
                fail("la vista previa no informa de la fila mala y del aviso: " + check.withProblems().errors()
                        + " " + check.withProblems().warnings());
            }
            csvDialog = CsvImportDialog.create(stage, "synclife-tareas.csv", check.withProblems());
            csvDialog.show();
        }));
        steps.add(() -> shot("importar-csv", csvDialog.getDialogPane().getScene()));
        steps.add(() -> csvDialog.close());
        steps.add(() -> main.navigator().navigate(View.AJUSTES));
        steps.add(() -> {
            if (stage.getScene().lookup("#settings-page") instanceof javafx.scene.control.ScrollPane page) {
                page.setVvalue(page.getVmax());
            }
        });
        steps.add(() -> shot("ajustes-datos"));
        // Matriz: clasificar una tarea y comprobar que aparece en su cuadrante
        steps.add(() -> FxAsync.run(() -> {
            Task task = context.tasks().quickAdd("Clasificar en la matriz", Area.TRABAJO, null, null);
            context.tasks().setQuadrant(task.getId(), Quadrant.DELEGAR);
            boolean found = context.tasks().matrix(Area.TRABAJO).of(Quadrant.DELEGAR).stream()
                    .anyMatch(t -> t.getId().equals(task.getId()));
            context.tasks().delete(task.getId());
            return found;
        }, found -> {
            if (!found) {
                fail("la tarea clasificada no aparece en su cuadrante");
            }
        }));
        // Pomodoro: empezar con una tarea, comprobar la cuenta atrás y terminar (< 1 min: no se registra)
        steps.add(() -> FxAsync.run(() -> context.tasks().tasksForDay(context.today()).stream()
                .filter(t -> t.isOpen() && !t.isRoutineInstance()).findFirst().orElseThrow(), task -> {
            pomodoroTaskId = task.getId();
            pomodoroMinutesBefore = task.getTrackedMinutes();
            main.navigator().navigate(View.HOY);
            main.pomodoro().start(task);
        }));
        steps.add(() -> {
            if (!main.pomodoro().isActive() || !stage.getTitle().contains("Enfoque")) {
                fail("el pomodoro no arrancó (título: " + stage.getTitle() + ")");
            }
            if (AppUi.notifications().shownCount() == 0) {
                fail("no se mostró el aviso de inicio del pomodoro");
            }
            shot("pomodoro-activo");
        });
        steps.add(() -> {
            main.pomodoro().stop();
            if (main.pomodoro().isActive() || !stage.getTitle().equals("SyncLife")) {
                fail("el pomodoro no se detuvo");
            }
        });
        steps.add(() -> FxAsync.run(() -> context.tasks().findById(pomodoroTaskId).orElseThrow(), task -> {
            if (task.getStatus() != TaskStatus.EN_PROGRESO) {
                fail("la tarea del pomodoro no pasó a «En progreso»");
            }
            if (task.getTrackedMinutes() != pomodoroMinutesBefore) {
                fail("se registró tiempo de una sesión de menos de un minuto");
            }
        }));
        // Borrado múltiple
        steps.add(() -> FxAsync.run(() -> {
            Task first = context.tasks().quickAdd("Borrar 1", Area.VIDA, context.today(), null);
            Task second = context.tasks().quickAdd("Borrar 2", Area.VIDA, null, null);
            return context.tasks().deleteAll(List.of(first.getId(), second.getId()));
        }, result -> {
            if (!result.equals(new DeleteResult(2, 0))) {
                fail("borrado múltiple inesperado: " + result);
            }
        }));
        steps.add(() -> FxAsync.run(() -> {
            Task task = context.tasks().quickAdd("Prueba de humo", Area.TRABAJO, context.today(), null);
            Task done = context.tasks().toggleDone(task.getId());
            context.tasks().delete(task.getId());
            return done;
        }, done -> {
            if (done.getStatus() != TaskStatus.COMPLETADA) {
                fail("la tarea no quedó completada");
            }
        }));
        steps.add(() -> {
            Theme.apply(ThemeMode.OSCURO);
            main.navigator().navigate(View.SEMANA);
        });
        steps.add(() -> shot("semana-oscuro"));
        steps.add(() -> main.navigator().navigate(View.MATRIZ));
        steps.add(() -> shot("matriz-oscuro"));
        steps.add(() -> Theme.apply(context.settings().getTheme()));
        // Borrar todos los datos (lo último: deja la base de datos vacía). Primero, el diálogo de confirmación
        steps.add(() -> FxAsync.run(() -> context.dataReset().summary(), summary -> {
            resetDialog = Dialogs.typedConfirmation("¿Borrar todos los datos?",
                    SettingsController.resetMessage(summary), "BORRAR", "Borrar todo");
            resetDialog.show();
        }));
        steps.add(() -> shot("confirmar-borrado", resetDialog.getDialogPane().getScene()));
        steps.add(() -> resetDialog.close());
        // Después, el botón de Ajustes (en modo humo la confirmación se da por aceptada)
        steps.add(() -> main.navigator().navigate(View.AJUSTES));
        steps.add(() -> {
            if (!(stage.getScene().lookup("#reset-data") instanceof Button reset)) {
                fail("no se encuentra «Borrar todos los datos» en Ajustes");
                return;
            }
            reset.fire();
        });
        steps.add(() -> FxAsync.run(() -> context.dataReset().summary(), summary -> {
            if (!summary.isEmpty() || summary.categories() != 11) {
                fail("«Borrar todos los datos» no dejó la base de datos como nueva: " + summary);
            }
            if (main.pomodoro().taskProperty().get() != null) {
                fail("la tarjeta del pomodoro sigue mostrando una tarea borrada");
            }
            try (var copies = Files.list(AppPaths.backupsDir(context.dataDir()))) {
                if (copies.findAny().isEmpty()) {
                    fail("no se guardó la copia de seguridad antes de borrar");
                }
            } catch (IOException e) {
                fail("no se guardó la copia de seguridad antes de borrar: " + e.getMessage());
            }
            shot("datos-borrados");
        }));
        steps.add(() -> main.navigator().navigate(View.HOY));
        steps.add(() -> shot("hoy-vacio"));
        next();
    }

    private java.util.Optional<atlantafx.base.controls.Notification> reminderToast() {
        return stage.getScene().getRoot().lookupAll(".toast").stream()
                .filter(n -> n instanceof atlantafx.base.controls.Notification notification
                        && notification.getMessage().contains("recordatorio de prueba"))
                .map(atlantafx.base.controls.Notification.class::cast).findFirst();
    }

    private ReviewController review() {
        return main.navigator().controller(View.REVISION);
    }

    private void next() {
        Runnable step = steps.poll();
        if (step == null) {
            finish();
            return;
        }
        try {
            step.run();
        } catch (Throwable e) {
            LOG.log(Level.SEVERE, "Paso de la prueba de humo fallido", e);
            fail(e.toString());
        }
        waitUntilIdle();
    }

    /** Espera a que no quede trabajo de base de datos pendiente y a que se pinte la pantalla. */
    private void waitUntilIdle() {
        PauseTransition pause = new PauseTransition(Duration.millis(400));
        pause.setOnFinished(e -> {
            if (FxAsync.isIdle()) {
                next();
            } else {
                waitUntilIdle();
            }
        });
        pause.play();
    }

    private void finish() {
        int errors = Dialogs.errorCount();
        if (errors > 0) {
            fail(errors + " error(es) durante la ejecución (ver log)");
        }
        System.out.println(exitCode == 0 ? "SMOKE OK" : "SMOKE FAILED");
        Platform.exit();
    }

    private void shot(String name) {
        shot(name, stage.getScene());
    }

    private void shot(String name, Scene scene) {
        if (shotsDir == null || scene == null) {
            return;
        }
        WritableImage image = scene.snapshot(null);
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, width, height, pixels, 0, width);
        try {
            Files.createDirectories(shotsDir);
            ImageIO.write(buffered, "png", shotsDir.resolve(name + ".png").toFile());
        } catch (IOException e) {
            fail("no se pudo guardar la captura " + name + ": " + e.getMessage());
        }
    }
}
