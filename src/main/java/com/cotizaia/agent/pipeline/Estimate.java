package com.cotizaia.agent.pipeline;

import java.math.BigDecimal;
import java.util.List;

/** One strategy's answer: deterministic hours plus the evidence that produced them. */
public record Estimate(BigDecimal hours, EstimateSource source, List<Citation> citations) {

    public Estimate {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
