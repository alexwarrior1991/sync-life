package com.alejandro.synclife.persistence.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Guarda {@link LocalTime} como texto {@code HH:mm} (precisión de minutos). */
@Converter(autoApply = true)
public class LocalTimeConverter implements AttributeConverter<LocalTime, String> {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public String convertToDatabaseColumn(LocalTime value) {
        return value == null ? null : value.format(FORMAT);
    }

    @Override
    public LocalTime convertToEntityAttribute(String value) {
        return value == null || value.isBlank() ? null : LocalTime.parse(value, FORMAT);
    }
}
