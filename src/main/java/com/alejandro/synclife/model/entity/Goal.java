package com.alejandro.synclife.model.entity;

import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.GoalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Objetivo a largo plazo dentro de un área (p. ej. "Correr una 10K" en Deporte). Las tareas se vinculan a él
 * con {@link Task#getGoal()}; su avance es la proporción de tareas vinculadas completadas.
 */
@Entity
@Table(name = "goal")
public class Goal extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Area area = Area.TRABAJO;

    /** Fecha objetivo (opcional). */
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private GoalStatus status = GoalStatus.EN_CURSO;

    private LocalDateTime completedAt;

    public Goal() {
    }

    public Goal(String title, Area area) {
        this.title = title;
        this.area = area;
    }

    public boolean isOpen() {
        return status.isOpen();
    }

    /** Sigue en curso y ya pasó su fecha objetivo. */
    public boolean isOverdue(LocalDate today) {
        return isOpen() && targetDate != null && targetDate.isBefore(today);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Area getArea() {
        return area;
    }

    public void setArea(Area area) {
        this.area = area;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public void setTargetDate(LocalDate targetDate) {
        this.targetDate = targetDate;
    }

    public GoalStatus getStatus() {
        return status;
    }

    public void setStatus(GoalStatus status) {
        this.status = status;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    @Override
    public String toString() {
        return title;
    }
}
