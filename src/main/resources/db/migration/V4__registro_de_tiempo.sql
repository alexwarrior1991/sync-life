-- Tiempo real dedicado a una tarea (sesiones Pomodoro o apuntes manuales).
-- Al borrar la tarea se borra su registro de tiempo.

CREATE TABLE time_entry (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id     INTEGER     NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    started_at  VARCHAR(19) NOT NULL,
    ended_at    VARCHAR(19) NOT NULL CHECK (ended_at >= started_at),
    minutes     INTEGER     NOT NULL CHECK (minutes BETWEEN 1 AND 1440),
    source      VARCHAR(10) NOT NULL DEFAULT 'POMODORO' CHECK (source IN ('POMODORO', 'MANUAL')),
    created_at  VARCHAR(19) NOT NULL,
    updated_at  VARCHAR(19) NOT NULL
);

CREATE INDEX ix_time_entry_task ON time_entry (task_id);
CREATE INDEX ix_time_entry_started ON time_entry (started_at);
