package com.cotizaia.service;

import com.cotizaia.domain.Contract;
import com.cotizaia.domain.Payment;
import com.cotizaia.domain.PaymentKind;
import com.cotizaia.domain.Proposal;
import com.cotizaia.payment.PaymentCharge;
import com.cotizaia.payment.PaymentGateway;
import com.cotizaia.payment.PaymentReceipt;
import com.cotizaia.repository.PaymentRepository;
import java.math.RoundingMode;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordinates the acceptance deposit (project.txt section 4).
 * SIMULATED — no real money moves; the database also enforces one deposit.
 */
@Service
public class PaymentService {

    private final PaymentGateway gateway;
    private final PaymentRepository payments;

    public PaymentService(PaymentGateway gateway, PaymentRepository payments) {
        this.gateway = gateway;
        this.payments = payments;
    }

    @Transactional
    public Payment recordSimulatedDeposit(Proposal proposal, Contract contract) {
        if (proposal == null || proposal.getId() == null) {
            throw new IllegalArgumentException("persisted proposal is required");
        }
        if (payments.existsByProposalIdAndKind(proposal.getId(), PaymentKind.DEPOSIT)) {
            throw new IllegalStateException("Deposit already exists for proposal: " + proposal.getId());
        }
        PaymentCharge charge = new PaymentCharge(proposal.getId(),
                proposal.getTotal().multiply(Payment.DEPOSIT_SHARE).setScale(2, RoundingMode.HALF_UP),
                "COP", "Simulated 50 percent deposit");
        PaymentReceipt receipt = gateway.charge(charge);
        if (!receipt.simulated()) {
            throw new IllegalStateException("Only simulated receipts are accepted");
        }
        return payments.saveAndFlush(Payment.simulatedDeposit(
                proposal, contract, receipt.providerRef(), receipt.paidAt()));
    }

    @Transactional(readOnly = true)
    public Optional<Payment> findDeposit(Long proposalId) {
        return payments.findByProposalIdAndKind(proposalId, PaymentKind.DEPOSIT);
    }
}
