package com.cotizaia.agent.pipeline;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds pipeline planning defaults (project.txt section 2) without embedding deployment settings in handlers.
 * Positive values keep fallback estimation meaningful and prevent division by zero in phase planning.
 */
@ConfigurationProperties("pipeline")
@Component
public class PipelineProperties {

    private int defaultHours = 8;

    private int hoursPerWeek = 40;

    public int getDefaultHours() {
        return defaultHours;
    }

    public void setDefaultHours(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("pipeline.default-hours must be positive");
        }
        defaultHours = value;
    }

    public int getHoursPerWeek() {
        return hoursPerWeek;
    }

    public void setHoursPerWeek(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("pipeline.hours-per-week must be positive");
        }
        hoursPerWeek = value;
    }
}
