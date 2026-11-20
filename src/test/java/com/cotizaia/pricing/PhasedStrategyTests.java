package com.cotizaia.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PhasedStrategyTests {

    private final PhasedStrategy strategy = new PhasedStrategy(new PricingProperties(null, null));

    private static PricingRequest request() {
        return new PricingRequest(List.of(new PricingLine("app", "Aplicación", new BigDecimal("100"))),
                new BigDecimal("100000"));
    }

    @Test
    void addsTheTenPercentCoordinationOverhead() {
        PricingQuote quote = strategy.price(request());

        assertThat(quote.model()).isEqualTo(PricingModel.PHASED);
        assertThat(quote.subtotal()).isEqualByComparingTo("11000000.00");
    }

    @Test
    void paymentsFollowTheFourPhases() {
        PricingQuote quote = strategy.price(request());

        assertThat(quote.milestones()).extracting(PaymentMilestone::name)
                .containsExactly("Discovery & design", "Development", "QA", "Launch");
        assertThat(quote.milestones()).extracting(PaymentMilestone::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("2200000.00"), new BigDecimal("5500000.00"),
                        new BigDecimal("1650000.00"), new BigDecimal("1650000.00"));
    }

    @Test
    void theSharesCoverTheWholeProject() {
        BigDecimal shares = strategy.price(request()).milestones().stream().map(PaymentMilestone::share)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(shares).isEqualByComparingTo("1");
    }

    @Test
    void anEmptyQuoteCostsNothingInEveryPhase() {
        PricingQuote quote = strategy.price(new PricingRequest(List.of(), new BigDecimal("100000")));

        assertThat(quote.subtotal()).isEqualByComparingTo("0");
        assertThat(quote.milestones()).hasSize(4).allSatisfy(m -> assertThat(m.amount()).isEqualByComparingTo("0"));
    }
}
