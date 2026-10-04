package com.cotizaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One clause of a {@link Contract} (project.txt section 9 schema:
 * Propuesta -> Contrato -> Clausula). It stores the clause text and its display
 * order in the contract.
 *
 * <p>Design pattern - <b>Composition</b>: the clause is a composed child, only
 * created through {@link Contract#addClause(String)}, so it never exists
 * without its aggregate root and shares its lifecycle. The order is assigned by
 * the contract, which keeps the sequence dense and unique without the caller
 * managing it.
 */
@Entity
@Table(name = "clauses")
public class Clause {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    /** Display order in the contract; "order" is a reserved SQL word. */
    @Column(name = "clause_order", nullable = false)
    private int order;

    @Column(nullable = false)
    private String text;

    protected Clause() {
    }

    Clause(Contract contract, String text, int order) {
        if (contract == null) {
            throw new IllegalArgumentException("contract must not be null");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (order < 1) {
            throw new IllegalArgumentException("order must be positive");
        }
        this.contract = contract;
        this.text = text;
        this.order = order;
    }

    /** Detaches the clause when its contract drops it, keeping both sides in sync. */
    void detach() {
        this.contract = null;
    }

    public Long getId() {
        return id;
    }

    public Contract getContract() {
        return contract;
    }

    public int getOrder() {
        return order;
    }

    public String getText() {
        return text;
    }
}
