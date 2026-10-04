package com.cotizaia.agent.pipeline;

import com.cotizaia.document.DocumentData;
import com.cotizaia.document.DocumentType;
import com.cotizaia.pricing.PricingQuote;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Captures the completed quote (project.txt section 2) for a later atomic facade transaction.
 * Mutable estimate values and PDF bytes are copied on input and output so further context edits cannot alter it;
 * requirement types remain catalog references for subsequent persistence reconciliation.
 */
public record QuoteDraft(String projectType, List<RequirementDraft> requirements, PricingQuote quote,
        List<AmbiguityFinding> findings, String summary, Map<DocumentType, byte[]> documents,
        List<DocumentData.PhaseData> phasePlan) {

    public QuoteDraft {
        requirements = copyRequirements(requirements);
        findings = List.copyOf(findings);
        documents = copyDocuments(documents);
        phasePlan = List.copyOf(phasePlan);
    }

    @Override
    public List<RequirementDraft> requirements() {
        return copyRequirements(requirements);
    }

    @Override
    public Map<DocumentType, byte[]> documents() {
        return copyDocuments(documents);
    }

    private static List<RequirementDraft> copyRequirements(List<RequirementDraft> requirements) {
        return requirements.stream().map(RequirementDraft::copy).toList();
    }

    private static Map<DocumentType, byte[]> copyDocuments(Map<DocumentType, byte[]> documents) {
        Map<DocumentType, byte[]> copy = new EnumMap<>(DocumentType.class);
        documents.forEach((type, bytes) -> copy.put(type, bytes.clone()));
        return Map.copyOf(copy);
    }
}
