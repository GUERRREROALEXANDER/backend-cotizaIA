package com.cotizaia.payment;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adapter that produces a local receipt (project.txt section 4).
 * SIMULATED — no real money moves. A real processor would be another
 * implementation of this port selected by configuration; this is deliberately
 * the only implementation in the academic demo.
 * <p>Design pattern - <b>Adapter</b>: this adapter implements the
 * {@link PaymentGateway} target and produces a provider-shaped receipt locally.
 */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulatedPaymentGateway.class);

    @Override
    public PaymentReceipt charge(PaymentCharge charge) {
        if (charge == null || charge.amount() == null || charge.amount().signum() <= 0) {
            throw new IllegalArgumentException("charge amount must be positive");
        }
        LOGGER.info("SIMULATED payment, no real money moved");
        return new PaymentReceipt("SIM-" + UUID.randomUUID(), Instant.now(), true);
    }
}
