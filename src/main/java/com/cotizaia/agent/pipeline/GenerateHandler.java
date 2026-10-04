package com.cotizaia.agent.pipeline;

import com.cotizaia.agent.llm.LlmClient;
import com.cotizaia.agent.llm.PromptBuilder;
import com.cotizaia.document.DocumentData;
import com.cotizaia.document.DocumentFactory;
import com.cotizaia.domain.DefaultContractClauses;
import com.cotizaia.pricing.PricedLine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Produces a summary and three provisional PDFs in memory (project.txt section 6) using existing document generators.
 * Planning uses a deterministic effort split so the model cannot invent prices, payment terms, or phase durations.
 *
 * <p>Design pattern - <b>Chain of Responsibility</b>: this ConcreteHandler renders artifacts after ambiguity checking;
 * {@link PipelineHandler} forwards the completed artifacts to the composition successor.
 */
@Component
public class GenerateHandler extends PipelineHandler {

    private final LlmClient client;

    private final PromptBuilder prompts;

    private final DocumentFactory documents;

    private final PipelineProperties properties;

    public GenerateHandler(LlmClient client, PromptBuilder prompts, DocumentFactory documents,
            PipelineProperties properties) {
        this.client = client;
        this.prompts = prompts;
        this.documents = documents;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "Generate";
    }

    @Override
    protected HandlerResult process(PipelineContext context) {
        List<String> names = context.getRequirements().stream()
                .map(draft -> draft.getRequirementType().getName()).toList();
        context.setSummary(client.complete(prompts.summarize(context.analysisText(),
                context.getProjectType(), names)).content());
        List<DocumentData.PhaseData> phases = phases(context);
        context.setPhasePlan(phases);
        List<DocumentData.LineData> lines = context.getQuote().lines().stream()
                .map(this::line).toList();
        BigDecimal subtotal = context.getQuote().subtotal();
        DocumentData data = new DocumentData(context.getAgencyName(), context.getClientName(),
                "Brief #" + context.getBrief().getId(), LocalDate.now(), context.getSummary(),
                context.getQuote().model(), lines, subtotal, List.of(), subtotal,
                subtotal.multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP), phases,
                DefaultContractClauses.DEFAULT_CLAUSES);
        context.setDocuments(documents.renderAll(data));
        return HandlerResult.proceed("three PDFs generated");
    }

    private DocumentData.LineData line(PricedLine line) {
        return new DocumentData.LineData(line.label(), line.hours(), line.unitPrice(), line.lineTotal());
    }

    private List<DocumentData.PhaseData> phases(PipelineContext context) {
        BigDecimal totalHours = context.getRequirements().stream().map(RequirementDraft::getEstimatedHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<DocumentData.PhaseData> result = new ArrayList<>();
        String[] names = {"Discovery & design", "Development", "QA", "Launch"};
        String[] shares = {"0.20", "0.50", "0.15", "0.15"};
        int start = 1;
        for (int index = 0; index < names.length; index++) {
            int weeks = Math.max(1, totalHours.multiply(new BigDecimal(shares[index]))
                    .divide(BigDecimal.valueOf(properties.getHoursPerWeek()), 0, RoundingMode.CEILING)
                    .intValueExact());
            result.add(new DocumentData.PhaseData(names[index], weeks, start, start + weeks - 1));
            start += weeks;
        }
        return result;
    }
}
