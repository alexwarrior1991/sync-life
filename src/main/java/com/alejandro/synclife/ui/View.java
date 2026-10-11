package com.alejandro.synclife.ui;

import javafx.scene.input.KeyCode;

/**
 * Pantallas de la barra lateral, en orden, con su atajo Ctrl+número. Los números están fijados
 * (no salen del orden) para que no cambien al añadir pantallas nuevas.
 */
public enum View {
    HOY("today.fxml", "Hoy", "mdi2c-calendar-today", true, KeyCode.DIGIT1),
    SEMANA("week.fxml", "Semana", "mdi2c-calendar-week", true, KeyCode.DIGIT2),
    TAREAS("tasks.fxml", "Tareas", "mdi2f-format-list-checks", true, KeyCode.DIGIT3),
    MATRIZ("matrix.fxml", "Matriz", "mdi2v-view-grid-outline", true, KeyCode.DIGIT4),
    OBJETIVOS("goals.fxml", "Objetivos", "mdi2b-bullseye-arrow", true, KeyCode.DIGIT5),
    RUTINAS("routines.fxml", "Rutinas", "mdi2r-repeat", true, KeyCode.DIGIT6),
    CATEGORIAS("categories.fxml", "Categorías", "mdi2t-tag-multiple-outline", true, KeyCode.DIGIT7),
    REVISION("review.fxml", "Revisión semanal", "mdi2c-calendar-check-outline", false, KeyCode.DIGIT8),
    ESTADISTICAS("stats.fxml", "Estadísticas", "mdi2c-chart-bar", false, KeyCode.DIGIT9),
    AJUSTES("settings.fxml", "Ajustes", "mdi2c-cog-outline", false, KeyCode.DIGIT0);

    private final String fxml;
    private final String title;
    private final String iconLiteral;
    private final boolean primary;
    private final KeyCode shortcut;

    View(String fxml, String title, String iconLiteral, boolean primary, KeyCode shortcut) {
        this.fxml = fxml;
        this.title = title;
        this.iconLiteral = iconLiteral;
        this.primary = primary;
        this.shortcut = shortcut;
    }

    public String fxml() {
        return fxml;
    }

    public String title() {
        return title;
    }

    public String iconLiteral() {
        return iconLiteral;
    }

    /** Las principales van arriba en la barra lateral; el resto, abajo. */
    public boolean isPrimary() {
        return primary;
    }

    /** Tecla que, con Ctrl, abre la pantalla. */
    public KeyCode shortcut() {
        return shortcut;
    }

    /** "Ctrl+4". */
    public String shortcutText() {
        return "Ctrl+" + shortcut.getName();
    }
}
