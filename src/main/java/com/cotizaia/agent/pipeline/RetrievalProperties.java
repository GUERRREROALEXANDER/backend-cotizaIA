package com.cotizaia.agent.pipeline;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds how much past evidence a requirement must bring before memory may price it.
 * The floor and neighbour minimum keep one weak lookalike from overriding the catalog.
 */
@ConfigurationProperties("pipeline.retrieval")
@Component
public class RetrievalProperties {

    private boolean enabled = true;

    private int k = 5;

    /** Inclusive similarity floor: lower hits are noise and never justify an estimate. */
    private double minSimilarity = 0.15;

    private int minNeighbors = 2;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getK() {
        return k;
    }

    public void setK(int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("pipeline.retrieval.k must be positive");
        }
        this.k = k;
    }

    public double getMinSimilarity() {
        return minSimilarity;
    }

    public void setMinSimilarity(double minSimilarity) {
        this.minSimilarity = minSimilarity;
    }

    public int getMinNeighbors() {
        return minNeighbors;
    }

    public void setMinNeighbors(int minNeighbors) {
        if (minNeighbors < 0) {
            throw new IllegalArgumentException("pipeline.retrieval.min-neighbors must not be negative");
        }
        this.minNeighbors = minNeighbors;
    }
}
