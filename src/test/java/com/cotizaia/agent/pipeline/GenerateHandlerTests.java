package com.cotizaia.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotizaia.agent.llm.LlmClient;
import com.cotizaia.agent.llm.LlmRequest;
import com.cotizaia.agent.llm.LlmResponse;
import com.cotizaia.agent.llm.PromptBuilder;
import com.cotizaia.document.DocumentData;
import com.cotizaia.document.DocumentFactory;
import com.cotizaia.document.DocumentType;
import com.cotizaia.pricing.PaymentMilestone;
import com.cotizaia.pricing.PricedLine;
import com.cotizaia.pricing.PricingModel;
import com.cotizaia.pricing.PricingQuote;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Checks summary and phase planning without rendering PDFs or using Spring. */
class GenerateHandlerTests {

    @Test
    void buildsSummaryDocumentsAndSequentialPhases() {
        PipelineContext context = PipelineTestFixture.context();
        context.setProjectType("WEB_SITE");
        RequirementDraft requirement = PipelineTestFixture.requirement(new BigDecimal("40.00"));
        requirement.setEstimatedHours(new BigDecimal("40.00"));
        context.setRequirements(List.of(requirement));
        PricedLine line = new PricedLine(requirement.key(), "Web", new BigDecimal("40.00"),
                new BigDecimal("100.00"), new BigDecimal("4000.00"));
        context.setQuote(new PricingQuote(PricingModel.HOURLY, new BigDecimal("100.00"), List.of(line),
                new BigDecimal("4000.00"), List.of(
                        new PaymentMilestone("All", BigDecimal.ONE,
                                new BigDecimal("4000.00"))), "test"));
        DocumentFactory factory = mock(DocumentFactory.class);
        when(factory.renderAll(any(DocumentData.class))).thenReturn(Map.of(DocumentType.PROPOSAL, new byte[] {1}));
        LlmClient client = new LlmClient() {
            @Override
            public LlmResponse complete(LlmRequest request) {
                return new LlmResponse("Resumen", "fake", "fake", 0);
            }

            @Override
            public String provider() {
                return "fake";
            }
        };
        PipelineProperties properties = new PipelineProperties();
        properties.setHoursPerWeek(10);
        HandlerResult result = new GenerateHandler(client, new PromptBuilder(), factory, properties).process(context);
        assertThat(result.decision()).isEqualTo(HandlerDecision.CONTINUE);
        assertThat(context.getSummary()).isEqualTo("Resumen");
        assertThat(context.getDocuments()).hasSize(1);
        assertThat(context.getPhasePlan()).hasSize(4);
        assertThat(context.getPhasePlan().get(1).startWeek())
                .isEqualTo(context.getPhasePlan().get(0).endWeek() + 1);
        assertThat(context.getPhasePlan()).extracting(DocumentData.PhaseData::weeks).containsExactly(1, 2, 1, 1);
        ArgumentCaptor<DocumentData> data = ArgumentCaptor.forClass(DocumentData.class);
        verify(factory).renderAll(data.capture());
        assertThat(data.getValue().depositAmount()).isEqualByComparingTo("2000.00");
        assertThat(data.getValue().total()).isEqualByComparingTo("4000.00");
        assertThat(data.getValue().lines()).singleElement()
                .satisfies(priced -> assertThat(priced.hours()).isEqualByComparingTo("40.00"));
    }
}
