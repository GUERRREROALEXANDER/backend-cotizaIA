package com.cotizaia.agent.pipeline;

import com.cotizaia.document.DocumentData;
import com.cotizaia.document.DocumentType;
import com.cotizaia.domain.Brief;
import com.cotizaia.pricing.PricingQuote;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Holds one mutable agent pass (project.txt section 2) before a facade persists its complete business result.
 * All per-run state lives here so singleton handlers can process independent briefs without sharing their outputs.
 */
public class PipelineContext {

    private final Brief brief;

    private final Long agencyId;

    private final String agencyName;

    private final String clientName;

    private final List<Clarification> clarifications;

    private String projectType;

    private BigDecimal classificationConfidence;

    private List<RequirementDraft> requirements = new ArrayList<>();

    private PricingQuote quote;

    private List<AmbiguityFinding> findings = new ArrayList<>();

    private String summary;

    private Map<DocumentType, byte[]> documents = Map.of();

    private List<DocumentData.PhaseData> phasePlan = List.of();

    private QuoteDraft draft;

    private boolean halted;

    private String haltedBy;

    private String haltReason;

    private String currentHandler;

    public PipelineContext(Brief brief, Long agencyId, String agencyName, String clientName,
            List<Clarification> clarifications) {
        this.brief = brief;
        this.agencyId = agencyId;
        this.agencyName = agencyName;
        this.clientName = clientName;
        this.clarifications = List.copyOf(clarifications);
    }

    public String analysisText() {
        StringBuilder text = new StringBuilder(brief.getRawText());
        for (Clarification clarification : clarifications) {
            text.append("\nAclaracion (").append(clarification.question()).append("): ")
                    .append(clarification.answer());
        }
        return text.toString();
    }

    public Set<String> resolvedCodes() {
        return clarifications.stream().map(Clarification::code).collect(Collectors.toSet());
    }

    public void halt(String handler, String reason) {
        halted = true;
        haltedBy = handler;
        haltReason = reason;
    }

    public Brief getBrief() {
        return brief;
    }

    public Long getAgencyId() {
        return agencyId;
    }

    public String getAgencyName() {
        return agencyName;
    }

    public String getClientName() {
        return clientName;
    }

    public String getProjectType() {
        return projectType;
    }

    public void setProjectType(String value) {
        projectType = value;
    }

    public BigDecimal getClassificationConfidence() {
        return classificationConfidence;
    }

    public void setClassificationConfidence(BigDecimal value) {
        classificationConfidence = value;
    }

    public List<RequirementDraft> getRequirements() {
        return requirements;
    }

    public void setRequirements(List<RequirementDraft> value) {
        requirements = value;
    }

    public PricingQuote getQuote() {
        return quote;
    }

    public void setQuote(PricingQuote value) {
        quote = value;
    }

    public List<AmbiguityFinding> getFindings() {
        return findings;
    }

    public void setFindings(List<AmbiguityFinding> value) {
        findings = value;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String value) {
        summary = value;
    }

    public Map<DocumentType, byte[]> getDocuments() {
        return documents;
    }

    public void setDocuments(Map<DocumentType, byte[]> value) {
        documents = value;
    }

    public List<DocumentData.PhaseData> getPhasePlan() {
        return phasePlan;
    }

    public void setPhasePlan(List<DocumentData.PhaseData> value) {
        phasePlan = value;
    }

    public QuoteDraft getDraft() {
        return draft;
    }

    public void setDraft(QuoteDraft value) {
        draft = value;
    }

    public boolean isHalted() {
        return halted;
    }

    public String getHaltedBy() {
        return haltedBy;
    }

    public String getHaltReason() {
        return haltReason;
    }

    public String getCurrentHandler() {
        return currentHandler;
    }

    public void setCurrentHandler(String value) {
        currentHandler = value;
    }
}
