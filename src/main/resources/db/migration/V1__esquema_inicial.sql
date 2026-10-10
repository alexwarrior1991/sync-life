-- Esquema inicial de SyncLife.
-- Escrito a mano porque el dialecto SQLite de Hibernate no genera claves foráneas
-- ni índices únicos compuestos. Fechas y horas se guardan como texto ISO (ver converters).

CREATE TABLE category (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        VARCHAR(80) NOT NULL COLLATE NOCASE,
    area        VARCHAR(20) NOT NULL CHECK (area IN ('TRABAJO', 'DEPORTE', 'VIDA')),
    color_hex   VARCHAR(7)  NOT NULL DEFAULT '#64748B',
    archived    BOOLEAN     NOT NULL DEFAULT 0,
    sort_order  INTEGER     NOT NULL DEFAULT 0,
    created_at  VARCHAR(19) NOT NULL,
    updated_at  VARCHAR(19) NOT NULL,
    CONSTRAINT uq_category_area_name UNIQUE (area, name)
);

CREATE TABLE routine (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    name             VARCHAR(120) NOT NULL,
    description      TEXT,
    area             VARCHAR(20)  NOT NULL CHECK (area IN ('TRABAJO', 'DEPORTE', 'VIDA')),
    category_id      INTEGER REFERENCES category (id) ON DELETE SET NULL,
    days_mask        INTEGER      NOT NULL CHECK (days_mask BETWEEN 1 AND 127),
    start_time       VARCHAR(5)   CHECK (start_time IS NULL OR start_time GLOB '[0-2][0-9]:[0-5][0-9]'),
    duration_minutes INTEGER      CHECK (duration_minutes IS NULL OR duration_minutes > 0),
    priority         INTEGER      NOT NULL DEFAULT 2 CHECK (priority BETWEEN 1 AND 4),
    active           BOOLEAN      NOT NULL DEFAULT 1,
    start_date       VARCHAR(10)  NOT NULL CHECK (start_date GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]-[0-3][0-9]'),
    end_date         VARCHAR(10)  CHECK (end_date IS NULL OR end_date >= start_date),
    created_at       VARCHAR(19)  NOT NULL,
    updated_at       VARCHAR(19)  NOT NULL
);

CREATE TABLE task (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    title             VARCHAR(200) NOT NULL,
    description       TEXT,
    area              VARCHAR(20)  NOT NULL CHECK (area IN ('TRABAJO', 'DEPORTE', 'VIDA')),
    category_id       INTEGER REFERENCES category (id) ON DELETE SET NULL,
    routine_id        INTEGER REFERENCES routine (id) ON DELETE SET NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE'
                      CHECK (status IN ('PENDIENTE', 'EN_PROGRESO', 'COMPLETADA', 'CANCELADA')),
    priority          INTEGER      NOT NULL DEFAULT 2 CHECK (priority BETWEEN 1 AND 4),
    scheduled_date    VARCHAR(10)  CHECK (scheduled_date IS NULL OR scheduled_date GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]-[0-3][0-9]'),
    start_time        VARCHAR(5)   CHECK (start_time IS NULL OR start_time GLOB '[0-2][0-9]:[0-5][0-9]'),
    estimated_minutes INTEGER      CHECK (estimated_minutes IS NULL OR estimated_minutes >= 0),
    due_date          VARCHAR(10)  CHECK (due_date IS NULL OR due_date GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]-[0-3][0-9]'),
    completed_at      VARCHAR(19),
    created_at        VARCHAR(19)  NOT NULL,
    updated_at        VARCHAR(19)  NOT NULL,
    CHECK (routine_id IS NULL OR scheduled_date IS NOT NULL)
);

-- Una rutina genera como mucho una tarea por día (en SQLite los NULL son distintos entre sí).
CREATE UNIQUE INDEX ux_task_routine_day ON task (routine_id, scheduled_date);
CREATE INDEX ix_task_scheduled_date ON task (scheduled_date);
CREATE INDEX ix_task_due_date ON task (due_date);
CREATE INDEX ix_task_status ON task (status);
CREATE INDEX ix_task_category ON task (category_id);
CREATE INDEX ix_routine_category ON routine (category_id);
