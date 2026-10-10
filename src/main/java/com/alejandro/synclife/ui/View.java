package com.alejandro.synclife.ui;

/** Pantallas de la barra lateral, en orden. */
public enum View {
    HOY("today.fxml", "Hoy", "mdi2c-calendar-today", true),
    SEMANA("week.fxml", "Semana", "mdi2c-calendar-week", true),
    TAREAS("tasks.fxml", "Tareas", "mdi2f-format-list-checks", true),
    RUTINAS("routines.fxml", "Rutinas", "mdi2r-repeat", true),
    CATEGORIAS("categories.fxml", "Categorías", "mdi2t-tag-multiple-outline", true),
    ESTADISTICAS("stats.fxml", "Estadísticas", "mdi2c-chart-bar", false),
    AJUSTES("settings.fxml", "Ajustes", "mdi2c-cog-outline", false);

    private final String fxml;
    private final String title;
    private final String iconLiteral;
    private final boolean primary;

    View(String fxml, String title, String iconLiteral, boolean primary) {
        this.fxml = fxml;
        this.title = title;
        this.iconLiteral = iconLiteral;
        this.primary = primary;
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
}
