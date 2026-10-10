package com.alejandro.synclife.ui;

/**
 * Opción de un ComboBox con texto propio. Permite opciones como "Todas las áreas" o
 * "Sin categoría" con valor {@code null}, algo que un ComboBox normal gestiona mal.
 */
public record Choice<T>(String label, T value) {

    public static <T> Choice<T> of(String label, T value) {
        return new Choice<>(label, value);
    }

    @Override
    public String toString() {
        return label;
    }
}
