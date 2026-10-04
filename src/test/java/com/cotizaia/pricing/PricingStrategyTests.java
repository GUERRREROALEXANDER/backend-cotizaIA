package com.cotizaia.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PricingStrategyTests {

    private final PricingProperties properties = new PricingProperties(null, null);
    private final PricingRequest request = new PricingRequest(List.of(
            new PricingLine("a", "Discovery", new BigDecimal("40")),
            new PricingLine("b", "Build", new BigDecimal("20")),
            new PricingLine("c", "QA", new BigDecimal("10"))), new BigDecimal("100000"));

    @Test
    void pricesTheSameHoursAcrossAllStrategies() {
        PricingQuote hourly = new HourlyStrategy(properties).price(request);
        PricingQuote fixed = new FixedPriceStrategy(properties).price(request);
        PricingQuote phased = new PhasedStrategy(properties).price(request);

        assertThat(hourly.subtotal()).isEqualByComparingTo("7000000.00");
        assertThat(fixed.subtotal()).isEqualByComparingTo("8400000.00");
        assertThat(phased.subtotal()).isEqualByComparingTo("7700000.00");
        assertThat(phased.milestones()).extracting(PaymentMilestone::amount)
                .containsExactly(new BigDecimal("1540000.00"), new BigDecimal("3850000.00"),
                        new BigDecimal("1155000.00"), new BigDecimal("1155000.00"));
        assertThat(hourly.lines()).extracting(PricedLine::key).containsExactly("a", "b", "c");
    }

    @Test
    void reconcilesAwkwardRoundingAndEmptyLines() {
        PricingRequest awkward = new PricingRequest(
                List.of(new PricingLine("a", "Work", new BigDecimal("7"))), new BigDecimal("33333.33"));
        for (PricingStrategy strategy : strategies()) {
            PricingQuote quote = strategy.price(awkward);
            assertThat(quote.milestones().stream().map(PaymentMilestone::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(quote.subtotal());
            PricingQuote empty = strategy.price(new PricingRequest(List.of(), new BigDecimal("33333.33")));
            assertThat(empty.subtotal()).isEqualByComparingTo("0.00");
            assertThat(empty.milestones()).allSatisfy(milestone ->
                    assertThat(milestone.amount()).isEqualByComparingTo("0.00"));
        }
    }

    @Test
    void resolvesEachModelAndRejectsMissingOrDuplicateModels() {
        PricingStrategyResolver resolver = new PricingStrategyResolver(strategies());
        for (PricingModel model : PricingModel.values()) {
            assertThat(resolver.forModel(model).model()).isEqualTo(model);
        }
        assertThatThrownBy(() -> new PricingStrategyResolver(List.of(new HourlyStrategy(properties)))
                .forModel(PricingModel.FIXED)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PricingStrategyResolver(List.of(
                new HourlyStrategy(properties), new HourlyStrategy(properties))))
                .isInstanceOf(IllegalStateException.class);
    }

    private List<PricingStrategy> strategies() {
        return List.of(new HourlyStrategy(properties), new FixedPriceStrategy(properties),
                new PhasedStrategy(properties));
    }
}
