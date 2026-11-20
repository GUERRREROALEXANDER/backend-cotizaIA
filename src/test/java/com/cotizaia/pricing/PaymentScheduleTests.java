package com.cotizaia.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentScheduleTests {

    private final PricingProperties properties = new PricingProperties(null, null);

    private static PricingRequest request(String hours, String rate) {
        return new PricingRequest(List.of(new PricingLine("web", "Sitio web", new BigDecimal(hours))),
                new BigDecimal(rate));
    }

    private static BigDecimal shares(PricingQuote quote) {
        return quote.milestones().stream().map(PaymentMilestone::share).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void fixedPriceIsPaidHalfUpFrontAndHalfOnDelivery() {
        PricingQuote quote = new FixedPriceStrategy(properties).price(request("10", "100000"));

        assertThat(quote.milestones()).extracting(PaymentMilestone::name).containsExactly("Anticipo", "Entrega");
        assertThat(shares(quote)).isEqualByComparingTo("1");
        assertThat(quote.subtotal()).isEqualByComparingTo("1200000.00");
    }

    @Test
    void phasedProjectsFollowTheFourPhases() {
        PricingQuote quote = new PhasedStrategy(properties).price(request("10", "100000"));

        assertThat(quote.milestones()).extracting(PaymentMilestone::name)
                .containsExactly("Discovery & design", "Development", "QA", "Launch");
        assertThat(quote.milestones()).extracting(PaymentMilestone::share)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("0.20"), new BigDecimal("0.50"), new BigDecimal("0.15"),
                        new BigDecimal("0.15"));
        assertThat(shares(quote)).isEqualByComparingTo("1");
        assertThat(quote.subtotal()).isEqualByComparingTo("1100000.00");
    }

    @Test
    void hourlyWorkIsOneMilestoneAtTheBareRate() {
        PricingQuote quote = new HourlyStrategy(properties).price(request("10", "100000"));

        assertThat(quote.milestones()).singleElement()
                .satisfies(milestone -> assertThat(milestone.amount()).isEqualByComparingTo(quote.subtotal()));
        assertThat(quote.lines().get(0).unitPrice()).isEqualByComparingTo("100000.00");
    }

    @Test
    void theLastMilestoneAbsorbsTheRoundingCent() {
        PricingQuote quote = new FixedPriceStrategy(new PricingProperties(BigDecimal.ZERO, null))
                .price(request("1", "100.01"));

        assertThat(quote.milestones()).extracting(PaymentMilestone::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("50.01"), new BigDecimal("50.00"));
    }

    @Test
    void eachStrategyReportsItsOwnModel() {
        assertThat(new FixedPriceStrategy(properties).model()).isEqualTo(PricingModel.FIXED);
        assertThat(new HourlyStrategy(properties).model()).isEqualTo(PricingModel.HOURLY);
        assertThat(new PhasedStrategy(properties).model()).isEqualTo(PricingModel.PHASED);
    }
}
