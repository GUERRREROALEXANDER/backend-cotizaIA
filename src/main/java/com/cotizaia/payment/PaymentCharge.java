package com.cotizaia.payment;

import java.math.BigDecimal;

/** Request sent through the payment port; currently only simulated charges exist. */
public record PaymentCharge(Long proposalId, BigDecimal amount, String currency, String description) {
}
