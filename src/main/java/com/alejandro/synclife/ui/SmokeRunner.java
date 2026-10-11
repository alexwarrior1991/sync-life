package com.alejandro.synclife.ui;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.config.AppSettings.ThemeMode;
import com.alejandro.synclife.controller.MainController;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.Quadrant;
import com.alejandro.synclife.model.enums.TaskStatus;
import com.alejandro.synclife.service.DeleteResult;
import com.alejandro.synclife.service.DemoData;
import com.alejandro.synclife.ui.control.ChecklistEditor;
import com.alejandro.synclife.ui.control.TaskEditorDialog;
import com.alejandro.synclife.ui.control.TaskEditorDialog.EditorResult;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
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
    private String originalTitle;
    private int originalChecklist;
    private long pomodoroTaskId;
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
        next();
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
