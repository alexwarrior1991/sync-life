package com.alejandro.synclife.model.entity;

import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.Priority;
import com.alejandro.synclife.model.enums.Quadrant;
import com.alejandro.synclife.model.enums.TaskStatus;
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
import org.hibernate.annotations.Formula;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * Una tarea concreta. Puede ser puntual o una instancia generada por una {@link Routine}
 * (en cuyo caso {@link #getRoutine()} no es null y su fecha programada es fija).
 */
@Entity
@Table(name = "task")
public class Task extends BaseEntity {

    /** Para avisar "N minutos antes" de una tarea sin hora se toma esta. */
    public static final LocalTime DEFAULT_REMINDER_TIME = LocalTime.of(9, 0);

    @Column(nullable = false, length = 200)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Area area = Area.TRABAJO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_id")
    private Routine routine;

    /** Objetivo a largo plazo al que contribuye (opcional; puede ser de otra área). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id")
    private Goal goal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status = TaskStatus.PENDIENTE;

    @Convert(converter = PriorityConverter.class)
    @Column(nullable = false)
    private Priority priority = Priority.MEDIA;

    /** Día en el que planeo hacerla (null = sin programar). */
    private LocalDate scheduledDate;

    /** Hora opcional dentro del día programado. */
    private LocalTime startTime;

    private Integer estimatedMinutes;

    /** Fecha límite (deadline). */
    private LocalDate dueDate;

    private LocalDateTime completedAt;

    /** Cuándo avisar (null = sin recordatorio). */
    private LocalDateTime remindAt;

    /** Cuándo se mostró el aviso; null = pendiente de avisar. Así cada aviso sale una sola vez. */
    private LocalDateTime remindedAt;

    /** Marcas de la matriz de Eisenhower; null = sin clasificar. */
    private Boolean urgent;

    private Boolean important;

    // Agregados de solo lectura (subconsultas): la UI los muestra sin cargar las subtareas ni los tiempos.
    // Se actualizan al volver a leer la tarea de la base de datos.

    @Formula("(select count(*) from subtask s where s.task_id = id)")
    private int subtaskTotal;

    @Formula("(select count(*) from subtask s where s.task_id = id and s.done = 1)")
    private int subtaskDone;

    @Formula("(select coalesce(sum(e.minutes), 0) from time_entry e where e.task_id = id)")
    private int trackedMinutes;

    public Task() {
    }

    public Task(String title, Area area) {
        this.title = title;
        this.area = area;
    }

    public boolean isRoutineInstance() {
        return routine != null;
    }

    public boolean isOpen() {
        return status.isOpen();
    }

    public boolean isDone() {
        return status == TaskStatus.COMPLETADA;
    }

    /** Vencida: sigue abierta y su fecha límite ya pasó. */
    public boolean isOverdue(LocalDate today) {
        return isOpen() && dueDate != null && dueDate.isBefore(today);
    }

    public void markDone(LocalDateTime now) {
        status = TaskStatus.COMPLETADA;
        completedAt = now.truncatedTo(ChronoUnit.SECONDS);
    }

    public void reopen() {
        status = TaskStatus.PENDIENTE;
        completedAt = null;
    }

    /** Cambia el estado manteniendo {@code completedAt} coherente. */
    public void changeStatus(TaskStatus newStatus, LocalDateTime now) {
        if (newStatus == TaskStatus.COMPLETADA) {
            if (status != TaskStatus.COMPLETADA || completedAt == null) {
                markDone(now);
            }
        } else {
            status = newStatus;
            completedAt = null;
        }
    }

    /** Cuadrante de Eisenhower, o null si falta alguna de las dos marcas. */
    public Quadrant getQuadrant() {
        return Quadrant.of(urgent, important);
    }

    /** Fija las dos marcas a la vez; null las deja sin clasificar. */
    public void setQuadrant(Quadrant quadrant) {
        urgent = quadrant == null ? null : quadrant.isUrgent();
        important = quadrant == null ? null : quadrant.isImportant();
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

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Routine getRoutine() {
        return routine;
    }

    public void setRoutine(Routine routine) {
        this.routine = routine;
    }

    public Goal getGoal() {
        return goal;
    }

    public void setGoal(Goal goal) {
        this.goal = goal;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public void setScheduledDate(LocalDate scheduledDate) {
        this.scheduledDate = scheduledDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public Integer getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public void setEstimatedMinutes(Integer estimatedMinutes) {
        this.estimatedMinutes = estimatedMinutes;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getRemindAt() {
        return remindAt;
    }

    public void setRemindAt(LocalDateTime remindAt) {
        this.remindAt = remindAt;
    }

    public LocalDateTime getRemindedAt() {
        return remindedAt;
    }

    public void setRemindedAt(LocalDateTime remindedAt) {
        this.remindedAt = remindedAt;
    }

    /** Hora de referencia de los avisos relativos: la de la tarea o, si no tiene, las 9:00 de su día. */
    public LocalDateTime reminderBase() {
        return scheduledDate == null ? null : scheduledDate.atTime(startTime == null ? DEFAULT_REMINDER_TIME : startTime);
    }

    public Boolean getUrgent() {
        return urgent;
    }

    public void setUrgent(Boolean urgent) {
        this.urgent = urgent;
    }

    public Boolean getImportant() {
        return important;
    }

    public void setImportant(Boolean important) {
        this.important = important;
    }

    public int getSubtaskTotal() {
        return subtaskTotal;
    }

    public int getSubtaskDone() {
        return subtaskDone;
    }

    /** Minutos reales registrados (Pomodoro y apuntes manuales). */
    public int getTrackedMinutes() {
        return trackedMinutes;
    }

    @Override
    public String toString() {
        return title;
    }
}
