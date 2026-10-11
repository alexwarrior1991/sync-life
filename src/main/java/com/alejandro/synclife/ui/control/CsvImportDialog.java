package com.alejandro.synclife.ui.control;

import atlantafx.base.theme.Styles;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.service.TaskCsvService.ImportOptions;
import com.alejandro.synclife.service.TaskCsvService.ImportRow;
import com.alejandro.synclife.service.TaskCsvService.Issue;
import com.alejandro.synclife.service.TaskCsvService.Preview;
import com.alejandro.synclife.ui.Formats;
import com.alejandro.synclife.ui.Theme;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Vista previa de una importación CSV: qué se va a crear, qué filas se descartan y por qué, y las opciones.
 * Devuelve las opciones elegidas, o {@code null} si se cancela.
 */
public final class CsvImportDialog {

    private static final double ISSUE_ROW_HEIGHT = 30;

    /** Una línea del informe: error (la fila no se importa) o aviso (se importa con un valor por defecto). */
    private record IssueLine(boolean error, Issue issue) {
    }

    private static final class IssueCell extends ListCell<IssueLine> {
        @Override
        protected void updateItem(IssueLine item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(item.issue().toString());
            FontIcon icon = new FontIcon(item.error() ? "mdi2c-close-circle-outline" : "mdi2a-alert-outline");
            icon.getStyleClass().add(item.error() ? Styles.DANGER : Styles.WARNING);
            setGraphic(icon);
        }
    }

    private CsvImportDialog() {
    }

    public static Dialog<ImportOptions> create(Window owner, String fileName, Preview preview) {
        int count = preview.rows().size();
        Label summary = new Label(summary(preview));
        summary.setWrapText(true);
        summary.getStyleClass().add("text-bold");
        Label format = new Label("Codificación " + preview.charset() + " · separador «" + separatorName(preview.separator())
                + "» · columnas reconocidas: "
                + preview.columns().stream().map(c -> c.header()).collect(Collectors.joining(", ")));
        format.setWrapText(true);
        format.getStyleClass().addAll("text-muted", "text-small");

        VBox content = new VBox(10, summary, format);
        if (!preview.unknownHeaders().isEmpty()) {
            Label unknown = new Label("Columnas que se ignoran: " + String.join(", ", preview.unknownHeaders()));
            unknown.setWrapText(true);
            unknown.getStyleClass().addAll("text-muted", "text-small");
            content.getChildren().add(unknown);
        }
        if (count > 0) {
            content.getChildren().add(table(preview.rows()));
        }
        List<Issue> issues = new ArrayList<>(preview.errors());
        issues.addAll(preview.warnings());
        if (!issues.isEmpty()) {
            Label title = new Label(preview.errors().isEmpty() ? "Avisos (esas filas se importan con un valor por defecto)"
                    : "Filas que no se importarán y avisos");
            title.getStyleClass().add("text-bold");
            ListView<IssueLine> list = new ListView<>();
            preview.errors().forEach(issue -> list.getItems().add(new IssueLine(true, issue)));
            preview.warnings().forEach(issue -> list.getItems().add(new IssueLine(false, issue)));
            list.setCellFactory(view -> new IssueCell());
            list.setFixedCellSize(ISSUE_ROW_HEIGHT);
            list.setPrefHeight(Math.min(5, issues.size()) * ISSUE_ROW_HEIGHT + 4);
            content.getChildren().addAll(title, list);
        }

        CheckBox createCategories = new CheckBox("Crear las categorías que no existan");
        createCategories.setSelected(true);
        CheckBox skipIdentical = new CheckBox("Omitir las que ya tengas (mismo título, área y fechas)");
        skipIdentical.setSelected(true);
        Label hint = new Label("La importación solo añade tareas: no modifica ni borra las que ya tienes. "
                + "Los objetivos se asocian por su título si ya existen.");
        hint.setWrapText(true);
        hint.getStyleClass().addAll("text-muted", "text-small");
        content.getChildren().addAll(createCategories, skipIdentical, hint);

        Dialog<ImportOptions> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle("Importar tareas");
        dialog.setHeaderText("Importar «" + fileName + "»");
        ButtonType importButton = new ButtonType(count == 1 ? "Importar 1 tarea" : "Importar " + count + " tareas",
                ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(importButton, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getStyleClass().add("wide-dialog"); // AtlantaFX limita los diálogos a 600 px
        dialog.getDialogPane().lookupButton(importButton).setDisable(count == 0);
        dialog.setResizable(true);
        Theme.style(dialog.getDialogPane());
        dialog.setResultConverter(button -> button != importButton ? null
                : new ImportOptions(createCategories.isSelected(), skipIdentical.isSelected()));
        return dialog;
    }

    static String summary(Preview preview) {
        int count = preview.rows().size();
        StringBuilder text = new StringBuilder(count == 0 ? "No hay tareas que importar."
                : count == 1 ? "1 tarea lista para importar." : count + " tareas listas para importar.");
        long discarded = preview.errors().stream().filter(i -> i.line() > 0).count();
        if (discarded > 0) {
            text.append(' ').append(discarded == 1 ? "1 fila con errores no se importará."
                    : discarded + " filas con errores no se importarán.");
        }
        preview.errors().stream().filter(i -> i.line() <= 0).forEach(i -> text.append(' ').append(i.message()));
        return text.toString();
    }

    private static String separatorName(char separator) {
        return switch (separator) {
            case '\t' -> "tabulador";
            case ';' -> "punto y coma";
            case ',' -> "coma";
            default -> String.valueOf(separator);
        };
    }

    /** Columnas de ancho fijo salvo el título, que se queda con el resto. */
    private static TableView<ImportRow> table(List<ImportRow> rows) {
        TableView<ImportRow> table = new TableView<>();
        table.getItems().setAll(rows);
        table.setFixedCellSize(30);
        table.setPrefHeight(Math.min(8, rows.size()) * 30 + 40);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getColumns().add(fixed(column("Línea", row -> Integer.toString(row.line())), 70));
        TableColumn<ImportRow, String> title = column("Título", row -> row.task().getTitle());
        title.setMinWidth(160);
        title.setPrefWidth(260);
        table.getColumns().add(title);
        table.getColumns().add(fixed(column("Área", row -> row.task().getArea().getDisplayName()), 90));
        table.getColumns().add(fixed(column("Categoría", row -> row.categoryName() == null ? "" : row.categoryName()), 130));
        table.getColumns().add(fixed(column("Programada", row -> day(row.task())), 145));
        table.getColumns().add(fixed(column("Estado", row -> row.task().getStatus().getDisplayName()), 115));
        table.getColumns().add(fixed(column("Pasos", row -> row.checklist().isEmpty() ? ""
                : Integer.toString(row.checklist().size())), 64));
        return table;
    }

    private static String day(Task task) {
        if (task.getScheduledDate() == null) {
            return "";
        }
        return Formats.date(task.getScheduledDate())
                + (task.getStartTime() == null ? "" : " " + Formats.time(task.getStartTime()));
    }

    private static TableColumn<ImportRow, String> column(String name, Function<ImportRow, String> value) {
        TableColumn<ImportRow, String> column = new TableColumn<>(name);
        column.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(value.apply(cd.getValue())));
        column.setSortable(false);
        column.setReorderable(false);
        return column;
    }

    private static <S, T> TableColumn<S, T> fixed(TableColumn<S, T> column, double width) {
        column.setMinWidth(width);
        column.setPrefWidth(width);
        column.setMaxWidth(width);
        return column;
    }
}
