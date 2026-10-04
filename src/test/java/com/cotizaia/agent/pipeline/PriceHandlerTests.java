package com.cotizaia.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cotizaia.pricing.PricingLine;
import com.cotizaia.pricing.PricingModel;
import com.cotizaia.pricing.PricingQuote;
import com.cotizaia.pricing.PricingService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks that provisional pricing uses the agency's default strategy. */
class PriceHandlerTests {

    @Test
    void delegatesEstimatedLines() {
        PipelineContext context = PipelineTestFixture.context();
        RequirementDraft draft = PipelineTestFixture.requirement(new BigDecimal("24.00"));
        draft.setEstimatedHours(new BigDecimal("24.00"));
        context.setRequirements(List.of(draft));
        PricingService service = mock(PricingService.class);
        PricingQuote quote = new PricingQuote(PricingModel.HOURLY, BigDecimal.ONE, List.of(),
                BigDecimal.ZERO, List.of(), "test");
        when(service.quote(eq(1L), isNull(), anyList())).thenReturn(quote);
        HandlerResult result = new PriceHandler(service).process(context);
        assertThat(result.decision()).isEqualTo(HandlerDecision.CONTINUE);
        assertThat(context.getQuote()).isSameAs(quote);
        verify(service).quote(1L, null,
                List.of(new PricingLine(draft.key(), draft.getDescription(), draft.getEstimatedHours())));
    }

    @Test
    void skipsPricingWhenScopeIsEmpty() {
        PipelineContext context = PipelineTestFixture.context();
        PricingService service = mock(PricingService.class);
        HandlerResult result = new PriceHandler(service).process(context);
        assertThat(result).isEqualTo(HandlerResult.proceed("nothing to price"));
        assertThat(context.getQuote()).isNull();
        verifyNoInteractions(service);
    }
}
