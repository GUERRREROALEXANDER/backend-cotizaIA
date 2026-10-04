package com.cotizaia.domain;

import java.math.BigDecimal;

/**
 * Component of the Decorator pattern for proposal pricing (project.txt section
 * 6 pattern 6: "Cotización base -> UrgenciaDecorator -> DescuentoDecorator ->
 * GarantiaExtendidaDecorator ... sin crear clases
 * CotizacionConUrgenciaYDescuentoYGarantia").
 *
 * <p>A priced proposal exposes two amounts:
 * <ul>
 *   <li>{@link #subtotal()} - the item-derived quote. Extras never change it,
 *       so the base price of the work stays traceable.</li>
 *   <li>{@link #total()} - the commercial amount after every wrapper has
 *       applied. The concrete component returns the subtotal; each decorator
 *       adjusts the amount it receives.</li>
 * </ul>
 *
 * <p>The interface is intentionally tiny: a new extra is a new
 * {@link ProposalPriceDecorator} subclass that overrides {@link #total()}, with
 * no change to this interface, to {@link Proposal}, or to any existing
 * decorator.
 */
public interface PricedProposal {

    /** Quote from the quoted items; unaffected by extras. */
    BigDecimal subtotal();

    /** Commercial amount after all extras have been applied. */
    BigDecimal total();
}
