package com.alejandro.synclife.model;

import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Area;
import com.alejandro.synclife.model.enums.Quadrant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class QuadrantTest {

    @Test
    void marksMapToTheirQuadrant() {
        assertThat(Quadrant.of(true, true)).isEqualTo(Quadrant.HACER_YA);
        assertThat(Quadrant.of(false, true)).isEqualTo(Quadrant.PLANIFICAR);
        assertThat(Quadrant.of(true, false)).isEqualTo(Quadrant.DELEGAR);
        assertThat(Quadrant.of(false, false)).isEqualTo(Quadrant.ELIMINAR);
    }

    @Test
    void aMissingMarkMeansUnclassified() {
        assertThat(Quadrant.of(null, null)).isNull();
        assertThat(Quadrant.of(true, null)).isNull();
        assertThat(Quadrant.of(null, false)).isNull();
    }

    @ParameterizedTest
    @EnumSource(Quadrant.class)
    void settingAQuadrantOnATaskRoundTrips(Quadrant quadrant) {
        Task task = new Task("Informe", Area.TRABAJO);
        task.setQuadrant(quadrant);
        assertThat(task.getQuadrant()).isEqualTo(quadrant);
        assertThat(task.getUrgent()).isEqualTo(quadrant.isUrgent());
        assertThat(task.getImportant()).isEqualTo(quadrant.isImportant());
    }

    @Test
    void clearingTheQuadrantClearsBothMarks() {
        Task task = new Task("Informe", Area.TRABAJO);
        task.setQuadrant(Quadrant.HACER_YA);
        task.setQuadrant(null);
        assertThat(task.getUrgent()).isNull();
        assertThat(task.getImportant()).isNull();
    }
}
