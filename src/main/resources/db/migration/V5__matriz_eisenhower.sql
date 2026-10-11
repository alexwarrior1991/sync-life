-- Marcas de la matriz de Eisenhower, independientes de la prioridad.
-- NULL = sin clasificar: las tareas existentes no caen por defecto en ningún cuadrante.

ALTER TABLE task ADD COLUMN urgent BOOLEAN CHECK (urgent IS NULL OR urgent IN (0, 1));
ALTER TABLE task ADD COLUMN important BOOLEAN CHECK (important IS NULL OR important IN (0, 1));
