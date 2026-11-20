package com.cotizaia.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PricingInputValidationTests {

    private static final PricingLine LINE = new PricingLine("home", "Página de inicio", new BigDecimal("6"));

    @Test
    void aRequestNeedsLinesAndAPositiveRate() {
        assertThatThrownBy(() -> new PricingRequest(null, BigDecimal.TEN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PricingRequest(List.of(LINE), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PricingRequest(List.of(LINE), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PricingRequest(List.of(LINE), new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aRequestFreezesItsLines() {
        List<PricingLine> lines = new ArrayList<>(List.of(LINE));
        PricingRequest request = new PricingRequest(lines, BigDecimal.TEN);
        lines.clear();

        assertThat(request.lines()).containsExactly(LINE);
        assertThatThrownBy(() -> request.lines().add(LINE)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aLineNeedsAKeyALabelAndNonNegativeHours() {
        assertThatThrownBy(() -> new PricingLine(" ", "x", BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PricingLine("k", null, BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PricingLine("k", "x", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PricingLine("k", "x", new BigDecimal("-0.5")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new PricingLine("k", "x", BigDecimal.ZERO).hours()).isZero();
    }

    @Test
    void aQuoteWhoseMilestonesDoNotAddUpIsRejected() {
        List<PaymentMilestone> milestones = List.of(
                new PaymentMilestone("Anticipo", new BigDecimal("0.50"), new BigDecimal("50.00")),
                new PaymentMilestone("Entrega", new BigDecimal("0.50"), new BigDecimal("49.00")));

        assertThatThrownBy(() -> new PricingQuote(PricingModel.FIXED, BigDecimal.TEN, List.of(),
                new BigDecimal("100.00"), milestones, "x"))
                .hasMessageContaining("milestones must sum to subtotal");
    }

    @Test
    void marginsDefaultToTwentyAndTenPercentAndCannotBeNegative() {
        PricingProperties defaults = new PricingProperties(null, null);

        assertThat(defaults.fixedRiskBuffer()).isEqualByComparingTo("0.20");
        assertThat(defaults.phasedCoordinationOverhead()).isEqualByComparingTo("0.10");
        assertThatThrownBy(() -> new PricingProperties(new BigDecimal("-0.1"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PricingProperties(null, new BigDecimal("-0.1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
