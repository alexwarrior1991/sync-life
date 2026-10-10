package com.alejandro.synclife.service;

/** Error de negocio con un mensaje en español apto para mostrar al usuario. */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
