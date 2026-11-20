package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PhaseTests {

    private final Schedule schedule = mock(Schedule.class);

    @Test
    void aPhaseTakesAShareOfTheHours() {
        Phase phase = new Phase(schedule, "Desarrollo", new BigDecimal("0.50"), 1);

        assertThat(phase.getName()).isEqualTo("Desarrollo");
        assertThat(phase.getHoursShare()).isEqualByComparingTo("0.50");
        assertThat(phase.getOrder()).isEqualTo(1);
        assertThat(phase.getDependencies()).isEmpty();
    }

    @Test
    void theShareMustBeAboveZeroAndAtMostTheWholeProject() {
        assertThatThrownBy(() -> new Phase(schedule, "QA", BigDecimal.ZERO, 0)).hasMessageContaining("hoursShare");
        assertThatThrownBy(() -> new Phase(schedule, "QA", new BigDecimal("1.01"), 0))
                .hasMessageContaining("hoursShare");
        assertThat(new Phase(schedule, "Todo", BigDecimal.ONE, 0).getHoursShare()).isEqualByComparingTo("1");
    }

    @Test
    void placementSetsTheWeeks() {
        Phase phase = new Phase(schedule, "QA", new BigDecimal("0.15"), 2);

        phase.applyPlacement(2, 5, 6);

        assertThat(phase.getWeeks()).isEqualTo(2);
        assertThat(phase.getStartWeek()).isEqualTo(5);
        assertThat(phase.getEndWeek()).isEqualTo(6);
    }

    @Test
    void aPhaseCannotDependOnItself() {
        Phase design = new Phase(schedule, "Diseño", new BigDecimal("0.2"), 0);
        Phase build = new Phase(schedule, "Desarrollo", new BigDecimal("0.5"), 1);

        assertThatThrownBy(() -> new PhaseDependency(design, design)).hasMessageContaining("itself");
        assertThat(new PhaseDependency(build, design).getDependsOnPhase()).isSameAs(design);
    }

    @Test
    void dependenciesCannotBeChangedFromOutside() {
        Phase phase = new Phase(schedule, "QA", new BigDecimal("0.15"), 2);

        assertThatThrownBy(() -> phase.getDependencies().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void scheduleNameAndOrderAreValidated() {
        assertThatThrownBy(() -> new Phase(null, "QA", BigDecimal.ONE, 0)).hasMessageContaining("schedule");
        assertThatThrownBy(() -> new Phase(schedule, " ", BigDecimal.ONE, 0)).hasMessageContaining("name");
        assertThatThrownBy(() -> new Phase(schedule, "QA", BigDecimal.ONE, -1)).hasMessageContaining("order");
    }
}
