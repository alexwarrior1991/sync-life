package com.alejandro.synclife.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Revisión semanal: se hace al final de una semana para planificar la siguiente.
 * {@code weekStart} es el lunes de la semana <b>planificada</b> (una revisión por semana); los contadores
 * son una foto de cómo terminó la semana anterior, para el historial.
 */
@Entity
@Table(name = "weekly_review")
public class WeeklyReview extends BaseEntity {

    @Column(nullable = false, unique = true)
    private LocalDate weekStart;

    private String notes;

    @Column(nullable = false)
    private int completedCount;

    @Column(nullable = false)
    private int pendingCount;

    protected WeeklyReview() {
    }

    public WeeklyReview(LocalDate weekStart) {
        this.weekStart = weekStart;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public int getCompletedCount() {
        return completedCount;
    }

    public void setCompletedCount(int completedCount) {
        this.completedCount = completedCount;
    }

    public int getPendingCount() {
        return pendingCount;
    }

    public void setPendingCount(int pendingCount) {
        this.pendingCount = pendingCount;
    }
}
