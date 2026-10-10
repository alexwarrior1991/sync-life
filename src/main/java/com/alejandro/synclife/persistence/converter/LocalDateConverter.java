package com.alejandro.synclife.persistence.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDate;

/**
 * Guarda {@link LocalDate} como texto ISO ({@code 2026-10-12}).
 * Sin esto, sqlite-jdbc lo guardaría como milisegundos dependientes de la zona horaria de la JVM.
 */
@Converter(autoApply = true)
public class LocalDateConverter implements AttributeConverter<LocalDate, String> {

    @Override
    public String convertToDatabaseColumn(LocalDate value) {
        return value == null ? null : value.toString();
    }

    @Override
    public LocalDate convertToEntityAttribute(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }
}
