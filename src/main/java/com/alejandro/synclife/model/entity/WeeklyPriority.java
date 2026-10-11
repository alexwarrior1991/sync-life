package com.alejandro.synclife.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Una de las (como mucho) tres prioridades de la semana planificada en una revisión. */
@Entity
@Table(name = "weekly_priority")
public class WeeklyPriority extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false, updatable = false)
    private WeeklyReview review;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    /** 1, 2 o 3. */
    @Column(nullable = false)
    private int sortOrder;

    protected WeeklyPriority() {
    }

    public WeeklyPriority(WeeklyReview review, Task task, int sortOrder) {
        this.review = review;
        this.task = task;
        this.sortOrder = sortOrder;
    }

    public WeeklyReview getReview() {
        return review;
    }

    public Task getTask() {
        return task;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
