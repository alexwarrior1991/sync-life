package com.alejandro.synclife.model.enums;

/**
 * Cuadrantes de la matriz de Eisenhower, derivados de las marcas "urgente" e "importante" de la tarea
 * (independientes de su prioridad).
 */
public enum Quadrant {
    HACER_YA("Hacer ya", "Urgente e importante", true, true, "mdi2f-fire", "#DC2626"),
    PLANIFICAR("Planificar", "Importante, no urgente", false, true, "mdi2c-calendar-clock", "#2563EB"),
    DELEGAR("Delegar", "Urgente, no importante", true, false, "mdi2a-account-arrow-right", "#D97706"),
    ELIMINAR("Eliminar", "Ni urgente ni importante", false, false, "mdi2c-cancel", "#64748B");

    private final String displayName;
    private final String description;
    private final boolean urgent;
    private final boolean important;
    private final String iconLiteral;
    private final String colorHex;

    Quadrant(String displayName, String description, boolean urgent, boolean important,
             String iconLiteral, String colorHex) {
        this.displayName = displayName;
        this.description = description;
        this.urgent = urgent;
        this.important = important;
        this.iconLiteral = iconLiteral;
        this.colorHex = colorHex;
    }

    /** Cuadrante de unas marcas; null si alguna de las dos está sin decidir ("Sin clasificar"). */
    public static Quadrant of(Boolean urgent, Boolean important) {
        if (urgent == null || important == null) {
            return null;
        }
        for (Quadrant quadrant : values()) {
            if (quadrant.urgent == urgent && quadrant.important == important) {
                return quadrant;
            }
        }
        throw new IllegalStateException("Combinación imposible");
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isUrgent() {
        return urgent;
    }

    public boolean isImportant() {
        return important;
    }

    public String getIconLiteral() {
        return iconLiteral;
    }

    public String getColorHex() {
        return colorHex;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
