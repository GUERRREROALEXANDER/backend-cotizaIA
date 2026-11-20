package com.cotizaia.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class HourlyStrategyTests {

    private final HourlyStrategy strategy = new HourlyStrategy(new PricingProperties(null, null));

    private static PricingRequest request() {
        return new PricingRequest(List.of(
                new PricingLine("landing", "Landing page", new BigDecimal("10")),
                new PricingLine("seo", "SEO básico", new BigDecimal("4.5"))), new BigDecimal("80000"));
    }

    @Test
    void chargesTheRateWithoutAnyMargin() {
        PricingQuote quote = strategy.price(request());

        assertThat(quote.model()).isEqualTo(PricingModel.HOURLY);
        assertThat(quote.hourlyRate()).isEqualByComparingTo("80000");
        assertThat(quote.lines()).allSatisfy(line -> assertThat(line.unitPrice()).isEqualByComparingTo("80000"));
        assertThat(quote.subtotal()).isEqualByComparingTo("1160000.00");
    }

    @Test
    void eachLineIsHoursTimesRate() {
        PricingQuote quote = strategy.price(request());

        assertThat(quote.lines()).extracting(PricedLine::lineTotal)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("800000.00"), new BigDecimal("360000.00"));
    }

    @Test
    void thereIsASinglePaymentForTheHoursWorked() {
        PricingQuote quote = strategy.price(request());

        assertThat(quote.milestones()).singleElement().satisfies(milestone -> {
            assertThat(milestone.name()).isEqualTo("Pago por horas ejecutadas");
            assertThat(milestone.amount()).isEqualByComparingTo(quote.subtotal());
        });
    }
}
