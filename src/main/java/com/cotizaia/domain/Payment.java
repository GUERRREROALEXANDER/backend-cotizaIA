package com.cotizaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Records the 50 percent acceptance deposit (project.txt section 4).
 * SIMULATED — no real money moves. The factory enforces the acceptance state,
 * contract ownership and amount before persistence.
 */
@Entity
@Table(name = "payments")
public class Payment {

    public static final BigDecimal DEPOSIT_SHARE = new BigDecimal("0.50");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id", nullable = false)
    private Proposal proposal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentKind kind;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(name = "provider_ref", nullable = false, unique = true, length = 80)
    private String providerRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false)
    private boolean simulated;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    protected Payment() {
    }

    /** Creates the only permitted payment: SIMULATED — no real money moves. */
    public static Payment simulatedDeposit(Proposal proposal, Contract contract, String providerRef, Instant paidAt) {
        if (proposal == null || contract == null || contract.getProposal() != proposal) {
            throw new IllegalArgumentException("contract must belong to proposal");
        }
        if (proposal.getStatus() != ProposalStatus.ACCEPTED
                && proposal.getStatus() != ProposalStatus.CONTRACT_ISSUED) {
            throw new IllegalStateException("proposal must be accepted before deposit");
        }
        if (providerRef == null || providerRef.isBlank() || providerRef.length() > 80 || paidAt == null) {
            throw new IllegalArgumentException("provider reference and paid time are required");
        }
        BigDecimal amount = proposal.getTotal().multiply(DEPOSIT_SHARE).setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("deposit must be positive");
        }
        Payment payment = new Payment();
        payment.proposal = proposal;
        payment.contract = contract;
        payment.kind = PaymentKind.DEPOSIT;
        payment.amount = amount;
        payment.provider = "SIMULATED";
        payment.providerRef = providerRef;
        payment.status = PaymentStatus.SIMULATED_PAID;
        payment.simulated = true;
        payment.paidAt = paidAt;
        return payment;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Proposal getProposal() {
        return proposal;
    }

    public Contract getContract() {
        return contract;
    }

    public PaymentKind getKind() {
        return kind;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderRef() {
        return providerRef;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public boolean isSimulated() {
        return simulated;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}
