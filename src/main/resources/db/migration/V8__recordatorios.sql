-- Recordatorios: cuándo avisar de una tarea y cuándo se avisó (para no repetir el aviso).
-- Las rutinas pueden avisar N minutos antes de la hora de cada una de sus tareas.

ALTER TABLE task ADD COLUMN remind_at VARCHAR(19)
    CHECK (remind_at IS NULL OR remind_at GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]-[0-3][0-9]T[0-2][0-9]:[0-5][0-9]:[0-5][0-9]');
ALTER TABLE task ADD COLUMN reminded_at VARCHAR(19);
ALTER TABLE routine ADD COLUMN remind_minutes_before INTEGER
    CHECK (remind_minutes_before IS NULL OR remind_minutes_before BETWEEN 0 AND 1440);

CREATE INDEX ix_task_remind_at ON task (remind_at);
