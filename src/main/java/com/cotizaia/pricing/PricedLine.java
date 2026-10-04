package com.cotizaia.pricing;

import java.math.BigDecimal;

/** Returns one priced requirement with its original key for caller reconciliation. */
public record PricedLine(String key, String label, BigDecimal hours, BigDecimal unitPrice, BigDecimal lineTotal) {
}
