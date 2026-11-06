package com.cotizaia.agent.pipeline;

import com.cotizaia.agent.knowledge.RetrievedChunk;
import com.cotizaia.domain.RequirementType;
import java.math.BigDecimal;
import java.util.List;

/**
 * Keeps extracted catalog references and estimates in memory (project.txt section 2) until the complete quote exists.
 * A captured key reconciles pricing lines without creating an ExtractedRequirement row during the chain.
 * Evidence and citations travel with the draft so the persisted requirement can explain its hours.
 */
public class RequirementDraft {

    private final RequirementType requirementType;

    private final String key;

    private final String description;

    private final BigDecimal confidence;

    private BigDecimal estimatedHours;

    private EstimateSource estimateSource;

    private List<Citation> citations = List.of();

    private List<RetrievedChunk> evidence = List.of();

    private boolean defaultHoursUsed;

    public RequirementDraft(RequirementType requirementType, String description, BigDecimal confidence) {
        this(requirementType, description, confidence,
                requirementType.getId() == null ? requirementType.getName() : requirementType.getId().toString());
    }

    private RequirementDraft(RequirementType requirementType, String description, BigDecimal confidence, String key) {
        this.requirementType = requirementType;
        this.key = key;
        this.description = description;
        this.confidence = confidence;
    }

    RequirementDraft copy() {
        RequirementDraft copy = new RequirementDraft(requirementType, description, confidence, key);
        copy.setEstimatedHours(estimatedHours);
        copy.setEstimateSource(estimateSource);
        copy.setCitations(citations);
        copy.setEvidence(evidence);
        copy.setDefaultHoursUsed(defaultHoursUsed);
        return copy;
    }

    public String key() {
        return key;
    }

    public RequirementType getRequirementType() {
        return requirementType;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public BigDecimal getEstimatedHours() {
        return estimatedHours;
    }

    public void setEstimatedHours(BigDecimal estimatedHours) {
        this.estimatedHours = estimatedHours;
    }

    public EstimateSource getEstimateSource() {
        return estimateSource;
    }

    public void setEstimateSource(EstimateSource estimateSource) {
        this.estimateSource = estimateSource;
    }

    public List<Citation> getCitations() {
        return citations;
    }

    public void setCitations(List<Citation> citations) {
        this.citations = citations == null ? List.of() : List.copyOf(citations);
    }

    public List<RetrievedChunk> getEvidence() {
        return evidence;
    }

    public void setEvidence(List<RetrievedChunk> evidence) {
        this.evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public boolean isDefaultHoursUsed() {
        return defaultHoursUsed;
    }

    public void setDefaultHoursUsed(boolean defaultHoursUsed) {
        this.defaultHoursUsed = defaultHoursUsed;
    }
}
