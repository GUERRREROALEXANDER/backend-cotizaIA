package com.cotizaia.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PipelinePropertiesTests {

    @Test
    void defaultsAreEightHoursAndFortyHourWeeks() {
        PipelineProperties properties = new PipelineProperties();

        assertThat(properties.getDefaultHours()).isEqualTo(8);
        assertThat(properties.getHoursPerWeek()).isEqualTo(40);
    }

    @Test
    void valuesMustBePositive() {
        PipelineProperties properties = new PipelineProperties();

        assertThatThrownBy(() -> properties.setDefaultHours(0)).hasMessageContaining("default-hours");
        assertThatThrownBy(() -> properties.setHoursPerWeek(-1)).hasMessageContaining("hours-per-week");
        assertThat(properties.getDefaultHours()).isEqualTo(8);
    }

    @Test
    void positiveValuesAreKept() {
        PipelineProperties properties = new PipelineProperties();
        properties.setDefaultHours(6);
        properties.setHoursPerWeek(30);

        assertThat(properties.getDefaultHours()).isEqualTo(6);
        assertThat(properties.getHoursPerWeek()).isEqualTo(30);
    }
}
