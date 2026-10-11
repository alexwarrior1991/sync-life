package com.alejandro.synclife.model.enums;

/** De dónde sale un registro de tiempo. */
public enum TimeSource {
    POMODORO("Pomodoro"),
    MANUAL("Manual");

    private final String displayName;

    TimeSource(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
