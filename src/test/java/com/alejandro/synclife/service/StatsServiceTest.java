package com.alejandro.synclife.service;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.TestSupport;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.TimeSource;
import com.alejandro.synclife.service.StatsService.AreaStats;
import com.alejandro.synclife.service.StatsService.Deviation;
import com.alejandro.synclife.service.StatsService.EstimateAccuracy;
import com.alejandro.synclife.service.StatsService.Snapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;

import static com.alejandro.synclife.TestSupport.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class StatsServiceTest {

    @TempDir
    Path dir;
    AppContext ctx;

    @BeforeEach
    void setUp() {
        ctx = TestSupport.context(dir);
    }

    @AfterEach
    void tearDown() {
        ctx.close();
    }

    /** Tarea completada hoy con {@code estimated} minutos estimados y {@code tracked} registrados. */
    private Task done(String title, Area area, LocalDate scheduled, Integer estimated, int tracked) {
        Task task = new Task(title, area);
        task.setScheduledDate(scheduled);
        task.setEstimatedMinutes(estimated);
        Task saved = ctx.tasks().save(task);
        if (tracked > 0) {
            ctx.timeTracking().record(saved.getId(), TODAY.atTime(8, 0), TODAY.atTime(8, 0).plusMinutes(tracked),
                    tracked, TimeSource.MANUAL);
        }
        return ctx.tasks().toggleDone(saved.getId());
    }

    @Test
    void accuracyComparesEstimatesWithTrackedTime() {
        done("Clavada", Area.TRABAJO, TODAY, 60, 66);       // +10 % → acertada
        done("Se alargó", Area.TRABAJO, TODAY, 30, 60);     // ×2 → por encima
        done("Fue rápida", Area.TRABAJO, TODAY, 100, 50);   // ×0,5 → por debajo
        done("Sin estimar", Area.TRABAJO, TODAY, null, 20); // no cuenta
        done("Sin registrar", Area.TRABAJO, TODAY, 45, 0);  // no cuenta

        EstimateAccuracy accuracy = ctx.stats().compute(TODAY, TODAY).accuracy();

        assertThat(accuracy.tasks()).isEqualTo(3);
        assertThat(accuracy.onTarget()).isEqualTo(1);
        assertThat(accuracy.over()).isEqualTo(1);
        assertThat(accuracy.under()).isEqualTo(1);
        assertThat(accuracy.estimatedMinutes()).isEqualTo(190);
        assertThat(accuracy.trackedMinutes()).isEqualTo(176);
        assertThat(accuracy.onTargetRate()).isCloseTo(1.0 / 3, within(1e-9));
        // Las mayores desviaciones primero (×2 y ×0,5 pesan lo mismo; después la acertada)
        assertThat(accuracy.largest()).extracting(Deviation::title).last().isEqualTo("Clavada");
    }

    @Test
    void accuracyUsesTheCompletionDateNotTheScheduledDay() {
        // Programada hace un mes pero completada hoy (el reloj de los tests marca hoy)
        done("Atrasada", Area.TRABAJO, TODAY.minusMonths(1), 30, 30);

        assertThat(ctx.stats().compute(TODAY, TODAY).accuracy().tasks()).isEqualTo(1);
        assertThat(ctx.stats().compute(TODAY.minusMonths(1), TODAY.minusMonths(1)).accuracy().tasks()).isZero();
    }

    @Test
    void trackedMinutesAreReportedPerArea() {
        done("Informe", Area.TRABAJO, TODAY, 60, 50);
        done("Correr", Area.DEPORTE, TODAY, 30, 35);

        Snapshot snapshot = ctx.stats().compute(TODAY, TODAY.plusDays(6));

        assertThat(snapshot.byArea()).filteredOn(a -> a.area() == Area.TRABAJO)
                .extracting(AreaStats::minutesTracked).containsExactly(50L);
        assertThat(snapshot.minutesTracked()).isEqualTo(85);
        assertThat(snapshot.minutesCompleted()).isEqualTo(90); // estimado de las completadas
    }

    @Test
    void noDataGivesAnEmptyAccuracy() {
        EstimateAccuracy accuracy = ctx.stats().compute(TODAY, TODAY).accuracy();
        assertThat(accuracy.tasks()).isZero();
        assertThat(accuracy.overallRatio()).isZero();
        assertThat(accuracy.largest()).isEmpty();
    }
}
