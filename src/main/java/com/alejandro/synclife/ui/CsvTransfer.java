package com.alejandro.synclife.ui;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.service.TaskCsvService.ImportResult;
import com.alejandro.synclife.service.TaskCsvService.Issue;
import com.alejandro.synclife.service.TaskCsvService.Preview;
import com.alejandro.synclife.service.ValidationException;
import com.alejandro.synclife.ui.control.CsvImportDialog;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/** Exportar e importar tareas en CSV desde la interfaz: elegir fichero, vista previa y resultado. */
public final class CsvTransfer {

    /** Más que suficiente para decenas de miles de tareas; evita cargar en memoria un fichero equivocado. */
    static final long MAX_IMPORT_BYTES = 20L * 1024 * 1024;
    private static final int MAX_LISTED_ISSUES = 12;

    private record Loaded(String fileName, Preview preview) {
    }

    private CsvTransfer() {
    }

    /** Pide dónde guardar y exporta las tareas que devuelva {@code loader} (en el hilo de base de datos). */
    public static void exportTasks(Window owner, AppContext context, String fileName, Callable<List<Task>> loader) {
        FileChooser chooser = chooser("Exportar tareas");
        chooser.setInitialFileName(fileName);
        File file = chooser.showSaveDialog(owner);
        if (file == null) {
            return;
        }
        Path target = file.toPath();
        FxAsync.run(() -> {
            List<Task> tasks = loader.call();
            Files.write(target, context.csv().export(tasks));
            return tasks.size();
        }, count -> AppUi.notifications().success((count == 1 ? "1 tarea exportada" : count + " tareas exportadas")
                + " a " + target.getFileName() + ". Se abre con Excel o LibreOffice."));
    }

    /** Pide un fichero, muestra la vista previa y, si se acepta, crea las tareas; luego llama a {@code afterImport}. */
    public static void importTasks(Window owner, AppContext context, Runnable afterImport) {
        File file = chooser("Importar tareas").showOpenDialog(owner);
        if (file == null) {
            return;
        }
        Path source = file.toPath();
        FxAsync.run(() -> {
            if (Files.size(source) > MAX_IMPORT_BYTES) {
                throw new ValidationException("El fichero ocupa más de 20 MB: no parece una lista de tareas.");
            }
            return new Loaded(source.getFileName().toString(), context.csv().preview(Files.readAllBytes(source)));
        }, loaded -> CsvImportDialog.create(owner, loaded.fileName(), loaded.preview()).showAndWait()
                .ifPresent(options -> FxAsync.run(() -> context.csv().apply(loaded.preview(), options), result -> {
                    report(result);
                    afterImport.run();
                })));
    }

    private static FileChooser chooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV (Excel, LibreOffice)", "*.csv", "*.txt"),
                new FileChooser.ExtensionFilter("Todos los ficheros", "*.*"));
        return chooser;
    }

    private static void report(ImportResult result) {
        String summary = summary(result);
        if (result.issues().isEmpty()) {
            AppUi.notifications().success(summary);
            return;
        }
        List<Issue> issues = result.issues();
        String listed = issues.stream().limit(MAX_LISTED_ISSUES).map(Issue::toString).collect(Collectors.joining("\n"));
        if (issues.size() > MAX_LISTED_ISSUES) {
            listed += "\n… y " + (issues.size() - MAX_LISTED_ISSUES) + " más.";
        }
        Dialogs.info(summary, listed);
    }

    static String summary(ImportResult result) {
        StringBuilder text = new StringBuilder(result.created() == 1 ? "1 tarea importada"
                : result.created() + " tareas importadas");
        if (result.skippedIdentical() > 0) {
            text.append(", ").append(result.skippedIdentical())
                    .append(result.skippedIdentical() == 1 ? " omitida por estar repetida" : " omitidas por estar repetidas");
        }
        if (result.categoriesCreated() > 0) {
            text.append(", ").append(result.categoriesCreated())
                    .append(result.categoriesCreated() == 1 ? " categoría nueva" : " categorías nuevas");
        }
        return text.append('.').toString();
    }
}
