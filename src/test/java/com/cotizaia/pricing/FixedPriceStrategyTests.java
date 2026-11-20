package com.cotizaia.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class FixedPriceStrategyTests {

    private static PricingRequest request() {
        return new PricingRequest(List.of(new PricingLine("shop", "Tienda", new BigDecimal("10"))),
                new BigDecimal("100000"));
    }

    @Test
    void addsTheDefaultTwentyPercentRiskBufferToTheRate() {
        PricingQuote quote = new FixedPriceStrategy(new PricingProperties(null, null)).price(request());

        assertThat(quote.model()).isEqualTo(PricingModel.FIXED);
        assertThat(quote.lines().get(0).unitPrice()).isEqualByComparingTo("120000.00");
        assertThat(quote.subtotal()).isEqualByComparingTo("1200000.00");
    }

    @Test
    void theBufferComesFromConfiguration() {
        PricingProperties properties = new PricingProperties(new BigDecimal("0.05"), null);

        assertThat(new FixedPriceStrategy(properties).price(request()).subtotal()).isEqualByComparingTo("1050000.00");
    }

    @Test
    void halfIsPaidUpFrontAndHalfOnDelivery() {
        PricingQuote quote = new FixedPriceStrategy(new PricingProperties(null, null)).price(request());

        assertThat(quote.milestones()).extracting(PaymentMilestone::name).containsExactly("Anticipo", "Entrega");
        assertThat(quote.milestones()).allSatisfy(m -> assertThat(m.amount()).isEqualByComparingTo("600000.00"));
    }

    @Test
    void anOddSubtotalStillAddsUpExactly() {
        PricingRequest odd = new PricingRequest(List.of(new PricingLine("x", "X", new BigDecimal("1"))),
                new BigDecimal("0.03"));

        PricingQuote quote = new FixedPriceStrategy(new PricingProperties(BigDecimal.ZERO, null)).price(odd);

        BigDecimal paid = quote.milestones().stream().map(PaymentMilestone::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(paid).isEqualByComparingTo(quote.subtotal());
    }
}
