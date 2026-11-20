package com.cotizaia.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SimulatedPaymentGatewayTests {

    private final PaymentGateway gateway = new SimulatedPaymentGateway();

    private static PaymentCharge charge(String amount) {
        return new PaymentCharge(1L, amount == null ? null : new BigDecimal(amount), "COP", "Anticipo");
    }

    @Test
    void aPositiveChargeGetsASimulatedReceipt() {
        Instant before = Instant.now();
        PaymentReceipt receipt = gateway.charge(charge("1500000"));

        assertThat(receipt.simulated()).isTrue();
        assertThat(receipt.providerRef()).startsWith("SIM-");
        assertThat(receipt.paidAt()).isAfterOrEqualTo(before);
    }

    @Test
    void everyReceiptHasItsOwnReference() {
        assertThat(gateway.charge(charge("1")).providerRef()).isNotEqualTo(gateway.charge(charge("1")).providerRef());
    }

    @Test
    void zeroNegativeOrMissingAmountsAreRefused() {
        assertThatThrownBy(() -> gateway.charge(charge("0"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gateway.charge(charge("-10"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gateway.charge(charge(null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gateway.charge(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
