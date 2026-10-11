package com.alejandro.synclife.model;

/**
 * Un elemento de la lista de comprobación tal y como lo edita la interfaz.
 * {@code id == null} significa que es nuevo; el orden es el de la lista.
 */
public record ChecklistItem(Long id, String title, boolean done) {

    public ChecklistItem withDone(boolean value) {
        return new ChecklistItem(id, title, value);
    }

    public ChecklistItem withTitle(String value) {
        return new ChecklistItem(id, value, done);
    }
}
