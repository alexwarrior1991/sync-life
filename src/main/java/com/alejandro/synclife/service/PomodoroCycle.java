package com.alejandro.synclife.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Lógica del temporizador Pomodoro, sin JavaFX ni reloj propio: quien lo usa le pasa el instante
 * actual en cada llamada. El tiempo transcurrido se calcula a partir de instantes (no sumando
 * "ticks"), así que no se desvía aunque los ticks lleguen tarde o el equipo se suspenda.
 *
 * <p>Ciclo: ENFOQUE → descanso corto (o largo cada {@code longBreakEvery} enfoques) → parado.
 * Al terminar un enfoque se emite una {@link FocusSession} para registrar el tiempo; también al
 * detenerlo antes de tiempo, si se llegó a {@link #MIN_SESSION}.
 */
public final class PomodoroCycle {

    /** Por debajo de esto, una sesión detenida no se registra. */
    public static final Duration MIN_SESSION = Duration.ofSeconds(60);

    public enum Phase {
        IDLE("Parado"),
        FOCUS("Enfoque"),
        SHORT_BREAK("Descanso"),
        LONG_BREAK("Descanso largo");

        private final String displayName;

        Phase(String displayName) {
            this.displayName = displayName;
        }

        public boolean isBreak() {
            return this == SHORT_BREAK || this == LONG_BREAK;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public record Settings(Duration focus, Duration shortBreak, Duration longBreak, int longBreakEvery,
                           boolean autoStartBreaks) {

        public static Settings ofMinutes(int focus, int shortBreak, int longBreak, int longBreakEvery,
                                         boolean autoStartBreaks) {
            return new Settings(Duration.ofMinutes(focus), Duration.ofMinutes(shortBreak),
                    Duration.ofMinutes(longBreak), Math.max(1, longBreakEvery), autoStartBreaks);
        }
    }

    /** Un bloque de enfoque terminado: cuándo empezó y acabó y cuánto tiempo efectivo hubo (sin pausas). */
    public record FocusSession(Instant start, Instant end, Duration focused) {
        /** Minutos efectivos redondeados, como mínimo 1. */
        public int minutes() {
            return (int) Math.max(1, Math.round(focused.toSeconds() / 60.0));
        }
    }

    /** Cambio de fase producido por {@link #tick}; {@code session} solo viene al terminar un enfoque. */
    public record Transition(Phase from, Phase to, FocusSession session) {
        public Optional<FocusSession> focusSession() {
            return Optional.ofNullable(session);
        }
    }

    private Settings settings;
    private Phase phase = Phase.IDLE;
    /** Inicio del enfoque actual (para el registro). */
    private Instant focusStart;
    /** Tiempo acumulado de la fase antes de la última reanudación. */
    private Duration accumulated = Duration.ZERO;
    /** Desde cuándo corre el reloj; null si está en pausa (o parado). */
    private Instant runningSince;
    private int completedFocus;

    public PomodoroCycle(Settings settings) {
        this.settings = settings;
    }

    /** Los ajustes nuevos se aplican a partir de la siguiente fase. */
    public void setSettings(Settings settings) {
        this.settings = settings;
    }

    public Settings getSettings() {
        return settings;
    }

    public Phase phase() {
        return phase;
    }

    public boolean isRunning() {
        return phase != Phase.IDLE && runningSince != null;
    }

    public boolean isPaused() {
        return phase != Phase.IDLE && runningSince == null;
    }

    /** Enfoques completados desde que se creó el ciclo. */
    public int completedFocus() {
        return completedFocus;
    }

    /** Empieza un enfoque nuevo (si ya había uno en marcha, se descarta sin registrar). */
    public void start(Instant now) {
        phase = Phase.FOCUS;
        focusStart = now;
        accumulated = Duration.ZERO;
        runningSince = now;
    }

    public void pause(Instant now) {
        if (isRunning()) {
            accumulated = accumulated.plus(Duration.between(runningSince, now));
            runningSince = null;
        }
    }

    public void resume(Instant now) {
        if (isPaused()) {
            runningSince = now;
        }
    }

    public Duration elapsed(Instant now) {
        if (phase == Phase.IDLE) {
            return Duration.ZERO;
        }
        Duration running = runningSince == null ? Duration.ZERO : Duration.between(runningSince, now);
        return accumulated.plus(running);
    }

    public Duration length() {
        return switch (phase) {
            case FOCUS -> settings.focus();
            case SHORT_BREAK -> settings.shortBreak();
            case LONG_BREAK -> settings.longBreak();
            case IDLE -> Duration.ZERO;
        };
    }

    public Duration remaining(Instant now) {
        Duration remaining = length().minus(elapsed(now));
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /** Fracción completada de la fase actual (0–1). */
    public double progress(Instant now) {
        long total = length().toMillis();
        return total == 0 ? 0 : Math.min(1, (double) elapsed(now).toMillis() / total);
    }

    /**
     * Avanza el reloj. Si la fase actual ha terminado, pasa a la siguiente y lo devuelve.
     * Como mucho una transición por llamada: si se han perdido varias, las siguientes llamadas las procesan.
     */
    public Optional<Transition> tick(Instant now) {
        if (!isRunning() || elapsed(now).compareTo(length()) < 0) {
            return Optional.empty();
        }
        // Instante exacto en que terminó la fase (puede ser anterior a now si el tick llega tarde)
        Instant end = now.minus(elapsed(now).minus(length()));
        Phase from = phase;
        if (from == Phase.FOCUS) {
            completedFocus++;
            FocusSession session = new FocusSession(focusStart, end, settings.focus());
            Phase next = completedFocus % settings.longBreakEvery() == 0 ? Phase.LONG_BREAK : Phase.SHORT_BREAK;
            enter(next, end, settings.autoStartBreaks());
            return Optional.of(new Transition(from, next, session));
        }
        enter(Phase.IDLE, end, false);
        return Optional.of(new Transition(from, Phase.IDLE, null));
    }

    /**
     * Detiene el ciclo. Si estaba en un enfoque con al menos {@link #MIN_SESSION} de trabajo efectivo,
     * devuelve esa sesión parcial para registrarla.
     */
    public Optional<FocusSession> stop(Instant now) {
        FocusSession session = null;
        if (phase == Phase.FOCUS) {
            Duration focused = elapsed(now);
            if (focused.compareTo(MIN_SESSION) >= 0) {
                session = new FocusSession(focusStart, now, focused);
            }
        }
        enter(Phase.IDLE, now, false);
        return Optional.ofNullable(session);
    }

    /** Termina el descanso ya, esté corriendo o esperando a empezar ({@link #resume} lo empieza). */
    public void skipBreak(Instant now) {
        if (phase.isBreak()) {
            enter(Phase.IDLE, now, false);
        }
    }

    private void enter(Phase next, Instant at, boolean running) {
        phase = next;
        accumulated = Duration.ZERO;
        runningSince = next != Phase.IDLE && running ? at : null;
        if (next == Phase.IDLE) {
            focusStart = null;
        }
    }
}
