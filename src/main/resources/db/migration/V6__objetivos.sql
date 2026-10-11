-- Objetivos a largo plazo por área, con tareas vinculadas.
-- Al borrar un objetivo, sus tareas se conservan sin objetivo.

CREATE TABLE goal (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    title         VARCHAR(120) NOT NULL,
    description   TEXT,
    area          VARCHAR(20)  NOT NULL CHECK (area IN ('TRABAJO', 'DEPORTE', 'VIDA')),
    target_date   VARCHAR(10)  CHECK (target_date IS NULL OR target_date GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]-[0-3][0-9]'),
    status        VARCHAR(12)  NOT NULL DEFAULT 'EN_CURSO'
                  CHECK (status IN ('EN_CURSO', 'CONSEGUIDO', 'ABANDONADO')),
    completed_at  VARCHAR(19),
    created_at    VARCHAR(19)  NOT NULL,
    updated_at    VARCHAR(19)  NOT NULL
);

CREATE INDEX ix_goal_area_status ON goal (area, status);

-- En SQLite, una columna añadida con REFERENCES debe admitir NULL (y su valor por defecto es NULL)
ALTER TABLE task ADD COLUMN goal_id INTEGER REFERENCES goal (id) ON DELETE SET NULL;

CREATE INDEX ix_task_goal ON task (goal_id);
