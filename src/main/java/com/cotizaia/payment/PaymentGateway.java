package com.cotizaia.payment;

/**
 * Target port for charging a deposit (project.txt section 4).
 * <p>Design pattern - <b>Adapter</b>: a gateway implementation translates this
 * charge request into its provider behavior; the current adapter only simulates.
 */
public interface PaymentGateway {

    PaymentReceipt charge(PaymentCharge charge);
}
