package com.alejandro.synclife.persistence;

import com.alejandro.synclife.model.enums.Priority;
import com.alejandro.synclife.persistence.converter.LocalDateConverter;
import com.alejandro.synclife.persistence.converter.LocalDateTimeConverter;
import com.alejandro.synclife.persistence.converter.LocalTimeConverter;
import com.alejandro.synclife.persistence.converter.PriorityConverter;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class ConvertersTest {

    @Test
    void localDateAsIsoText() {
        LocalDateConverter c = new LocalDateConverter();
        assertThat(c.convertToDatabaseColumn(LocalDate.of(2026, 1, 5))).isEqualTo("2026-01-05");
        assertThat(c.convertToEntityAttribute("2026-01-05")).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(c.convertToDatabaseColumn(null)).isNull();
        assertThat(c.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void localTimeWithMinutePrecision() {
        LocalTimeConverter c = new LocalTimeConverter();
        assertThat(c.convertToDatabaseColumn(LocalTime.of(7, 5, 59))).isEqualTo("07:05");
        assertThat(c.convertToEntityAttribute("19:00")).isEqualTo(LocalTime.of(19, 0));
        assertThat(c.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void localDateTimeTruncatedToSeconds() {
        LocalDateTimeConverter c = new LocalDateTimeConverter();
        LocalDateTime value = LocalDateTime.of(2026, 10, 12, 8, 30, 0, 123_456_789);
        assertThat(c.convertToDatabaseColumn(value)).isEqualTo("2026-10-12T08:30:00");
        assertThat(c.convertToEntityAttribute("2026-10-12T08:30:00")).isEqualTo(value.withNano(0));
    }

    @Test
    void priorityAsWeight() {
        PriorityConverter c = new PriorityConverter();
        for (Priority p : Priority.values()) {
            assertThat(c.convertToEntityAttribute(c.convertToDatabaseColumn(p))).isEqualTo(p);
        }
        assertThat(c.convertToDatabaseColumn(Priority.URGENTE)).isEqualTo(4);
        assertThat(c.convertToDatabaseColumn(null)).isNull();
    }
}
