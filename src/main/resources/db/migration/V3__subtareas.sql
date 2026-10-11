-- Lista de comprobación (subtareas) dentro de una tarea.
-- Al borrar la tarea se borran sus subtareas.

CREATE TABLE subtask (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id     INTEGER      NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    title       VARCHAR(200) NOT NULL,
    done        BOOLEAN      NOT NULL DEFAULT 0,
    sort_order  INTEGER      NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    created_at  VARCHAR(19)  NOT NULL,
    updated_at  VARCHAR(19)  NOT NULL
);

CREATE INDEX ix_subtask_task ON subtask (task_id, sort_order);
