package com.cotizaia.payment;

import java.time.Instant;

/** Evidence returned by the payment port for a simulated deposit. */
public record PaymentReceipt(String providerRef, Instant paidAt, boolean simulated) {
}
