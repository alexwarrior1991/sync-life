package com.alejandro.synclife.service;

import com.alejandro.synclife.service.PomodoroCycle.FocusSession;
import com.alejandro.synclife.service.PomodoroCycle.Phase;
import com.alejandro.synclife.service.PomodoroCycle.Settings;
import com.alejandro.synclife.service.PomodoroCycle.Transition;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PomodoroCycleTest {

    private static final Instant T0 = Instant.parse("2026-10-12T08:00:00Z");

    private static Instant at(int minutes, int seconds) {
        return T0.plusSeconds(minutes * 60L + seconds);
    }

    private static PomodoroCycle cycle(boolean autoStartBreaks) {
        return new PomodoroCycle(Settings.ofMinutes(25, 5, 15, 4, autoStartBreaks));
    }

    @Test
    void countsDownAndFinishesTheFocusWithASession() {
        PomodoroCycle cycle = cycle(true);
        cycle.start(T0);
        assertThat(cycle.phase()).isEqualTo(Phase.FOCUS);
        assertThat(cycle.remaining(at(10, 0))).isEqualTo(Duration.ofMinutes(15));
        assertThat(cycle.tick(at(24, 59))).isEmpty();

        Transition transition = cycle.tick(at(25, 0)).orElseThrow();

        assertThat(transition.from()).isEqualTo(Phase.FOCUS);
        assertThat(transition.to()).isEqualTo(Phase.SHORT_BREAK);
        FocusSession session = transition.focusSession().orElseThrow();
        assertThat(session.start()).isEqualTo(T0);
        assertThat(session.end()).isEqualTo(at(25, 0));
        assertThat(session.minutes()).isEqualTo(25);
        assertThat(cycle.isRunning()).isTrue(); // el descanso empieza solo
    }

    @Test
    void aLateTickUsesTheExactEndAndTheBreakCountsFromThere() {
        PomodoroCycle cycle = cycle(true);
        cycle.start(T0);

        // El equipo estuvo suspendido: el tick llega 3 minutos tarde
        Transition transition = cycle.tick(at(28, 0)).orElseThrow();

        assertThat(transition.focusSession().orElseThrow().end()).isEqualTo(at(25, 0));
        assertThat(cycle.remaining(at(28, 0))).isEqualTo(Duration.ofMinutes(2));
        Transition breakOver = cycle.tick(at(31, 0)).orElseThrow();
        assertThat(breakOver.to()).isEqualTo(Phase.IDLE);
        assertThat(breakOver.focusSession()).isEmpty();
    }

    @Test
    void pausesDoNotCountAsFocus() {
        PomodoroCycle cycle = cycle(true);
        cycle.start(T0);
        cycle.pause(at(10, 0));
        assertThat(cycle.isPaused()).isTrue();
        assertThat(cycle.tick(at(40, 0))).isEmpty(); // en pausa no avanza
        cycle.resume(at(40, 0));
        assertThat(cycle.remaining(at(40, 0))).isEqualTo(Duration.ofMinutes(15));

        Transition transition = cycle.tick(at(55, 0)).orElseThrow();
        FocusSession session = transition.focusSession().orElseThrow();
        assertThat(session.start()).isEqualTo(T0);
        assertThat(session.end()).isEqualTo(at(55, 0));
        assertThat(session.focused()).isEqualTo(Duration.ofMinutes(25));
    }

    @Test
    void everyFourthFocusIsFollowedByALongBreak() {
        PomodoroCycle cycle = cycle(true);
        Instant now = T0;
        for (int i = 1; i <= 4; i++) {
            cycle.start(now);
            now = now.plus(Duration.ofMinutes(25));
            Transition transition = cycle.tick(now).orElseThrow();
            assertThat(transition.to()).isEqualTo(i == 4 ? Phase.LONG_BREAK : Phase.SHORT_BREAK);
            cycle.skipBreak(now);
        }
        assertThat(cycle.completedFocus()).isEqualTo(4);
    }

    @Test
    void withoutAutoStartTheBreakWaitsUntilResumed() {
        PomodoroCycle cycle = cycle(false);
        cycle.start(T0);
        cycle.tick(at(25, 0));

        assertThat(cycle.phase()).isEqualTo(Phase.SHORT_BREAK);
        assertThat(cycle.isPaused()).isTrue();
        assertThat(cycle.tick(at(45, 0))).isEmpty();
        cycle.resume(at(45, 0));
        assertThat(cycle.tick(at(50, 0)).orElseThrow().to()).isEqualTo(Phase.IDLE);
    }

    @Test
    void stoppingEarlyReturnsThePartialSessionOnlyAfterAMinute() {
        PomodoroCycle cycle = cycle(true);
        cycle.start(T0);
        Optional<FocusSession> tooShort = cycle.stop(at(0, 59));
        assertThat(tooShort).isEmpty();
        assertThat(cycle.phase()).isEqualTo(Phase.IDLE);

        cycle.start(at(5, 0));
        cycle.pause(at(10, 0));
        cycle.resume(at(20, 0));
        FocusSession partial = cycle.stop(at(22, 31)).orElseThrow();
        assertThat(partial.focused()).isEqualTo(Duration.ofSeconds(7 * 60 + 31));
        assertThat(partial.minutes()).isEqualTo(8);
        assertThat(partial.start()).isEqualTo(at(5, 0));
    }

    @Test
    void stoppingDuringABreakRecordsNothing() {
        PomodoroCycle cycle = cycle(true);
        cycle.start(T0);
        cycle.tick(at(25, 0));
        assertThat(cycle.stop(at(27, 0))).isEmpty();
        assertThat(cycle.phase()).isEqualTo(Phase.IDLE);
        assertThat(cycle.elapsed(at(30, 0))).isEqualTo(Duration.ZERO);
    }

    @Test
    void progressGoesFromZeroToOne() {
        PomodoroCycle cycle = cycle(true);
        assertThat(cycle.progress(T0)).isZero();
        cycle.start(T0);
        assertThat(cycle.progress(at(12, 30))).isEqualTo(0.5);
        assertThat(cycle.progress(at(30, 0))).isEqualTo(1.0);
    }
}
