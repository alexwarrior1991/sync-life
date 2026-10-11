package com.alejandro.synclife.service;

/** Resultado de un borrado múltiple: tareas borradas y tareas de rutina que se saltaron. */
public record DeleteResult(int deleted, int skippedRoutine) {
}
