-- Revisión semanal guiada: una por semana planificada (identificada por su lunes),
-- con notas, una foto de cómo fue la semana anterior y hasta tres prioridades.

CREATE TABLE weekly_review (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    week_start       VARCHAR(10) NOT NULL UNIQUE
                     CHECK (week_start GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]-[0-3][0-9]'
                            AND strftime('%w', week_start) = '1'),
    notes            TEXT,
    completed_count  INTEGER     NOT NULL DEFAULT 0 CHECK (completed_count >= 0),
    pending_count    INTEGER     NOT NULL DEFAULT 0 CHECK (pending_count >= 0),
    created_at       VARCHAR(19) NOT NULL,
    updated_at       VARCHAR(19) NOT NULL
);

CREATE TABLE weekly_priority (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    review_id   INTEGER     NOT NULL REFERENCES weekly_review (id) ON DELETE CASCADE,
    task_id     INTEGER     NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    sort_order  INTEGER     NOT NULL CHECK (sort_order BETWEEN 1 AND 3),
    created_at  VARCHAR(19) NOT NULL,
    updated_at  VARCHAR(19) NOT NULL,
    CONSTRAINT uq_weekly_priority_slot UNIQUE (review_id, sort_order),
    CONSTRAINT uq_weekly_priority_task UNIQUE (review_id, task_id)
);

CREATE INDEX ix_weekly_priority_task ON weekly_priority (task_id);
