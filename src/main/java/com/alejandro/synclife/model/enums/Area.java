package com.alejandro.synclife.model.enums;

/**
 * Las tres grandes áreas de la vida que organiza SyncLife.
 * Son fijas; dentro de cada una el usuario crea sus propias categorías.
 */
public enum Area {
    TRABAJO("Trabajo", "#2563EB", "mdi2b-briefcase-outline"),
    DEPORTE("Deporte", "#16A34A", "mdi2r-run-fast"),
    VIDA("Vida", "#D97706", "mdi2h-home-heart");

    private final String displayName;
    private final String colorHex;
    private final String iconLiteral;

    Area(String displayName, String colorHex, String iconLiteral) {
        this.displayName = displayName;
        this.colorHex = colorHex;
        this.iconLiteral = iconLiteral;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorHex() {
        return colorHex;
    }

    public String getIconLiteral() {
        return iconLiteral;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
