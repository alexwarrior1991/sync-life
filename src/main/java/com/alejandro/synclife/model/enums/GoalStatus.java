package com.alejandro.synclife.model.enums;

/** Estado de un objetivo a largo plazo. */
public enum GoalStatus {
    EN_CURSO("En curso"),
    CONSEGUIDO("Conseguido"),
    ABANDONADO("Abandonado");

    private final String displayName;

    GoalStatus(String displayName) {
        this.displayName = displayName;
    }

    public boolean isOpen() {
        return this == EN_CURSO;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
