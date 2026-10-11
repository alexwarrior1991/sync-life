package com.alejandro.synclife.ui.cell;

import com.alejandro.synclife.model.entity.Goal;
import com.alejandro.synclife.model.enums.GoalStatus;
import com.alejandro.synclife.service.GoalService.GoalProgress;
import com.alejandro.synclife.ui.Formats;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDate;
import java.util.function.Supplier;

/** Tarjeta de un objetivo en la lista: área, título, barra de avance y cuánto falta. */
public class GoalListCell extends ListCell<GoalProgress> {

    private static final PseudoClass CLOSED = PseudoClass.getPseudoClass("closed");
    private static final PseudoClass OVERDUE = PseudoClass.getPseudoClass("overdue");

    private final Supplier<LocalDate> today;
    private final FontIcon icon = new FontIcon();
    private final Label title = new Label();
    private final Label status = new Label();
    private final ProgressBar bar = new ProgressBar(0);
    private final Label count = new Label();
    private final Label when = new Label();
    private final VBox root;

    public GoalListCell(Supplier<LocalDate> today) {
        this.today = today;
        getStyleClass().add("goal-cell");
        title.getStyleClass().add("goal-title");
        title.setWrapText(true);
        title.setMinWidth(0);
        status.getStyleClass().addAll("badge", "badge-muted");
        status.setMinWidth(Region.USE_PREF_SIZE);
        icon.getStyleClass().add("goal-icon");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, icon, title, spacer, status);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.SOMETIMES);

        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("small");
        HBox.setHgrow(bar, Priority.ALWAYS);
        count.getStyleClass().addAll("text-muted", "text-small");
        count.setMinWidth(Region.USE_PREF_SIZE);
        HBox progress = new HBox(8, bar, count);
        progress.setAlignment(Pos.CENTER_LEFT);
        when.getStyleClass().addAll("goal-when", "text-small");

        root = new VBox(6, header, progress, when);
        root.setMinWidth(0);
        setPrefWidth(0); // nunca más ancha que la lista: sin barra de scroll horizontal
    }

    @Override
    protected void updateItem(GoalProgress item, boolean empty) {
        super.updateItem(item, empty);
        setText(null);
        if (empty || item == null) {
            setGraphic(null);
            return;
        }
        Goal goal = item.goal();
        LocalDate now = today.get();
        icon.setIconLiteral(goal.getArea().getIconLiteral());
        icon.setIconColor(Color.web(goal.getArea().getColorHex()));
        title.setText(goal.getTitle());
        status.setText(goal.getStatus().getDisplayName());
        status.setVisible(!goal.isOpen());
        status.setManaged(!goal.isOpen());
        bar.setProgress(item.rate());
        bar.setStyle("-color-progress-bar-fill: " + goal.getArea().getColorHex() + ";");
        count.setText(item.total() == 0 ? "Sin tareas" : item.done() + " / " + item.total());
        when.setText(whenText(goal, now));

        root.pseudoClassStateChanged(CLOSED, !goal.isOpen());
        root.pseudoClassStateChanged(OVERDUE, goal.isOverdue(now));
        setGraphic(root);
    }

    static String whenText(Goal goal, LocalDate today) {
        if (goal.getStatus() == GoalStatus.CONSEGUIDO && goal.getCompletedAt() != null) {
            return "Conseguido el " + Formats.date(goal.getCompletedAt().toLocalDate());
        }
        if (goal.getStatus() == GoalStatus.ABANDONADO) {
            return "Abandonado";
        }
        if (goal.getTargetDate() == null) {
            return "Sin fecha objetivo";
        }
        return Formats.daysLeft(goal.getTargetDate(), today) + " · " + Formats.date(goal.getTargetDate());
    }
}
