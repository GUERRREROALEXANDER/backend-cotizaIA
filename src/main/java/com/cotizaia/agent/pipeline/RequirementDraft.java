package com.cotizaia.agent.pipeline;

import com.cotizaia.domain.RequirementType;
import java.math.BigDecimal;

/**
 * Keeps extracted catalog references and estimates in memory (project.txt section 2) until the complete quote exists.
 * A captured key reconciles pricing lines without creating an ExtractedRequirement row during the chain.
 */
public class RequirementDraft {

    private final RequirementType requirementType;

    private final String key;

    private final String description;

    private final BigDecimal confidence;

    private BigDecimal estimatedHours;

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

    public boolean isDefaultHoursUsed() {
        return defaultHoursUsed;
    }

    public void setDefaultHoursUsed(boolean defaultHoursUsed) {
        this.defaultHoursUsed = defaultHoursUsed;
    }
}
