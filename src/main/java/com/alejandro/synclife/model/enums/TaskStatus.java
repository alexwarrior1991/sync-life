package com.alejandro.synclife.model.enums;

import java.util.EnumSet;
import java.util.Set;

public enum TaskStatus {
    PENDIENTE("Pendiente"),
    EN_PROGRESO("En progreso"),
    COMPLETADA("Completada"),
    CANCELADA("Cancelada");

    /** Estados que cuentan como "por hacer". */
    public static final Set<TaskStatus> OPEN = EnumSet.of(PENDIENTE, EN_PROGRESO);

    private final String displayName;

    TaskStatus(String displayName) {
        this.displayName = displayName;
    }

    public boolean isOpen() {
        return OPEN.contains(this);
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
