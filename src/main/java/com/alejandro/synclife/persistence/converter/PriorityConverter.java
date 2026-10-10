package com.alejandro.synclife.persistence.converter;

import com.alejandro.synclife.model.enums.Priority;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Guarda {@link Priority} como su peso (1-4) para poder ordenar por prioridad en SQL. */
@Converter
public class PriorityConverter implements AttributeConverter<Priority, Integer> {

    @Override
    public Integer convertToDatabaseColumn(Priority value) {
        return value == null ? null : value.getWeight();
    }

    @Override
    public Priority convertToEntityAttribute(Integer value) {
        return value == null ? null : Priority.fromWeight(value);
    }
}
