package com.alejandro.synclife.model.enums;

/**
 * Prioridad de una tarea. Se persiste como su peso numérico (1-4) para que
 * {@code order by priority desc} ordene de verdad por importancia.
 */
public enum Priority {
    BAJA(1, "Baja"),
    MEDIA(2, "Media"),
    ALTA(3, "Alta"),
    URGENTE(4, "Urgente");

    private final int weight;
    private final String displayName;

    Priority(int weight, String displayName) {
        this.weight = weight;
        this.displayName = displayName;
    }

    public int getWeight() {
        return weight;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Priority fromWeight(int weight) {
        for (Priority p : values()) {
            if (p.weight == weight) {
                return p;
            }
        }
        throw new IllegalArgumentException("Peso de prioridad desconocido: " + weight);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
