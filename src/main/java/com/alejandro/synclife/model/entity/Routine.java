package com.alejandro.synclife.model.entity;

import com.alejandro.synclife.model.DaysOfWeek;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.Priority;
import com.alejandro.synclife.persistence.converter.PriorityConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * Rutina recurrente (p. ej. "Gimnasio L-X-V 19:00"). No se marca como hecha:
 * genera una {@link Task} por cada día en que aplica, y es esa tarea la que se completa.
 */
@Entity
@Table(name = "routine")
public class Routine extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Area area = Area.TRABAJO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    /** Días en que aplica: bit0 = lunes … bit6 = domingo. Ver {@link DaysOfWeek}. */
    private int daysMask = DaysOfWeek.WEEKDAYS;

    private LocalTime startTime;

    private Integer durationMinutes;

    @Convert(converter = PriorityConverter.class)
    @Column(nullable = false)
    private Priority priority = Priority.MEDIA;

    private boolean active = true;

    /** Avisar estos minutos antes de cada tarea generada (null = sin aviso). */
    private Integer remindMinutesBefore;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    public Routine() {
    }

    public Routine(String name, Area area, int daysMask, LocalDate startDate) {
        this.name = name;
        this.area = area;
        this.daysMask = daysMask;
        this.startDate = startDate;
    }

    /** ¿Cae {@code date} dentro del calendario de la rutina (fechas y días), esté activa o no? */
    public boolean isScheduledOn(LocalDate date) {
        return startDate != null
                && !date.isBefore(startDate)
                && (endDate == null || !date.isAfter(endDate))
                && DaysOfWeek.contains(daysMask, date.getDayOfWeek());
    }

    /** ¿Debe existir una instancia de esta rutina en {@code date}? */
    public boolean appliesOn(LocalDate date) {
        return active && isScheduledOn(date);
    }

    /** Crea la tarea del día {@code date} a partir de esta rutina. */
    public Task newInstanceFor(LocalDate date) {
        Task task = new Task(name, area);
        task.setDescription(description);
        task.setCategory(category);
        task.setRoutine(this);
        task.setPriority(priority);
        task.setScheduledDate(date);
        task.setStartTime(startTime);
        task.setEstimatedMinutes(durationMinutes);
        if (remindMinutesBefore != null) {
            task.setRemindAt(task.reminderBase().minusMinutes(remindMinutesBefore));
        }
        return task;
    }

    @Transient
    public Set<DayOfWeek> getDays() {
        return DaysOfWeek.fromMask(daysMask);
    }

    public void setDays(Set<DayOfWeek> days) {
        this.daysMask = DaysOfWeek.toMask(days);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public int getDaysMask() {
        return daysMask;
    }

    public void setDaysMask(int daysMask) {
        this.daysMask = daysMask;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public Integer getRemindMinutesBefore() {
        return remindMinutesBefore;
    }

    public void setRemindMinutesBefore(Integer remindMinutesBefore) {
        this.remindMinutesBefore = remindMinutesBefore;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    @Override
    public String toString() {
        return name;
    }
}
