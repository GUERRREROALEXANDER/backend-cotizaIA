package com.cotizaia.agent.pipeline;

import com.cotizaia.agent.knowledge.RetrievedChunk;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Primary strategy: estimate from the agency's own most similar past items.
 *
 * <p>The median is weighted by similarity, so a near-identical past item counts much more than a loose one. The
 * strategy refuses to answer unless enough similar neighbours survive the floor: a wrong memory is worse than the
 * catalog fallback. Citations are the neighbours themselves, most similar first, so every memory estimate is
 * auditable.
 */
@Component
@Order(1)
public class RetrievalEstimation implements EstimationStrategy {

    private final RetrievalProperties properties;

    public RetrievalEstimation(RetrievalProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<Estimate> estimate(RequirementDraft draft) {
        List<RetrievedChunk> neighbors = draft.getEvidence().stream()
                .filter(hit -> hit.hours() != null && hit.similarity() >= properties.getMinSimilarity())
                .toList();
        if (neighbors.size() < properties.getMinNeighbors()) {
            return Optional.empty();
        }
        double totalWeight = neighbors.stream().mapToDouble(RetrievedChunk::similarity).sum();
        if (totalWeight <= 0) {
            return Optional.empty();
        }
        double half = totalWeight / 2.0;
        BigDecimal hours = null;
        double cumulative = 0;
        for (RetrievedChunk hit : neighbors.stream()
                .sorted(Comparator.comparing(RetrievedChunk::hours)).toList()) {
            cumulative += hit.similarity();
            if (cumulative >= half) {
                hours = hit.hours();
                break;
            }
        }
        if (hours == null) {
            return Optional.empty();
        }
        List<Citation> citations = neighbors.stream()
                .sorted(Comparator.comparingDouble(RetrievedChunk::similarity).reversed())
                .map(hit -> new Citation(hit.chunkId(), hit.documentName(), hit.page(), round(hit.similarity()),
                        hit.hours()))
                .toList();
        return Optional.of(new Estimate(hours, EstimateSource.MEMORY, citations));
    }

    private double round(double similarity) {
        return BigDecimal.valueOf(similarity).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }
}
