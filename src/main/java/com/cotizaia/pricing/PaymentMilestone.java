package com.cotizaia.pricing;

import java.math.BigDecimal;

/** Describes one share of a proposed payment schedule. */
public record PaymentMilestone(String name, BigDecimal share, BigDecimal amount) {
}
