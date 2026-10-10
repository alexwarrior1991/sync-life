package com.alejandro.synclife.persistence.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** Guarda {@link LocalDateTime} como texto ISO {@code yyyy-MM-ddTHH:mm:ss}. */
@Converter(autoApply = true)
public class LocalDateTimeConverter implements AttributeConverter<LocalDateTime, String> {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Override
    public String convertToDatabaseColumn(LocalDateTime value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.SECONDS).format(FORMAT);
    }

    @Override
    public LocalDateTime convertToEntityAttribute(String value) {
        return value == null || value.isBlank() ? null : LocalDateTime.parse(value, FORMAT);
    }
}
