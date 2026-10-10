package com.alejandro.synclife.model;

import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.Priority;
import com.alejandro.synclife.model.enums.TaskStatus;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/** Criterios de búsqueda de la pantalla "Tareas". Los campos null no filtran. */
public final class TaskFilter {

    private String text;
    private Area area;
    private Long categoryId;
    private Set<TaskStatus> statuses = EnumSet.allOf(TaskStatus.class);
    private Priority priority;
    private LocalDate scheduledFrom;
    private LocalDate scheduledTo;
    private boolean unscheduledOnly;
    private LocalDate dueBefore;
    private boolean includeRoutineInstances = true;

    public static TaskFilter all() {
        return new TaskFilter();
    }

    public TaskFilter text(String text) {
        this.text = text;
        return this;
    }

    public TaskFilter area(Area area) {
        this.area = area;
        return this;
    }

    public TaskFilter categoryId(Long categoryId) {
        this.categoryId = categoryId;
        return this;
    }

    public TaskFilter statuses(Set<TaskStatus> statuses) {
        this.statuses = statuses == null || statuses.isEmpty()
                ? EnumSet.allOf(TaskStatus.class)
                : EnumSet.copyOf(statuses);
        return this;
    }

    public TaskFilter priority(Priority priority) {
        this.priority = priority;
        return this;
    }

    public TaskFilter scheduledBetween(LocalDate from, LocalDate to) {
        this.scheduledFrom = from;
        this.scheduledTo = to;
        return this;
    }

    public TaskFilter unscheduledOnly(boolean unscheduledOnly) {
        this.unscheduledOnly = unscheduledOnly;
        return this;
    }

    /** Solo tareas cuya fecha límite es anterior a {@code date}. */
    public TaskFilter dueBefore(LocalDate date) {
        this.dueBefore = date;
        return this;
    }

    public TaskFilter includeRoutineInstances(boolean include) {
        this.includeRoutineInstances = include;
        return this;
    }

    public String getText() {
        return text;
    }

    public Area getArea() {
        return area;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public Set<TaskStatus> getStatuses() {
        return statuses;
    }

    public Priority getPriority() {
        return priority;
    }

    public LocalDate getScheduledFrom() {
        return scheduledFrom;
    }

    public LocalDate getScheduledTo() {
        return scheduledTo;
    }

    public boolean isUnscheduledOnly() {
        return unscheduledOnly;
    }

    public LocalDate getDueBefore() {
        return dueBefore;
    }

    public boolean isIncludeRoutineInstances() {
        return includeRoutineInstances;
    }
}
