package com.alejandro.synclife.model.entity;

import com.alejandro.synclife.model.enums.TimeSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Tiempo real dedicado a una tarea: una sesión de Pomodoro o un apunte manual.
 * {@code minutes} es el tiempo efectivo (sin pausas), así que puede ser menor que fin − inicio.
 */
@Entity
@Table(name = "time_entry")
public class TimeEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, updatable = false)
    private Task task;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    @Column(nullable = false)
    private LocalDateTime endedAt;

    @Column(nullable = false)
    private int minutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TimeSource source = TimeSource.POMODORO;

    protected TimeEntry() {
    }

    public TimeEntry(Task task, LocalDateTime startedAt, LocalDateTime endedAt, int minutes, TimeSource source) {
        this.task = task;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.minutes = minutes;
        this.source = source;
    }

    public Task getTask() {
        return task;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public int getMinutes() {
        return minutes;
    }

    public TimeSource getSource() {
        return source;
    }
}
