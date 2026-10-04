package com.cotizaia.service;

import com.cotizaia.domain.Contract;
import com.cotizaia.domain.ContractStatus;
import com.cotizaia.domain.Payment;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.domain.state.InvalidStateTransitionException;
import com.cotizaia.domain.state.ProposalEvent;
import com.cotizaia.repository.ContractRepository;
import com.cotizaia.repository.ProposalRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accepts a proposal and records its deposit atomically (project.txt section 4).
 * SIMULATED — no real money moves. Any failure rolls back contract issuance,
 * proposal transitions, notifications and the deposit together.
 */
@Service
public class ProposalAcceptanceService {

    private final ContractRepository contracts;
    private final ContractService contractService;
    private final PaymentService paymentService;
    private final ProposalRepository proposals;

    public ProposalAcceptanceService(ContractRepository contracts, ContractService contractService,
            PaymentService paymentService, ProposalRepository proposals) {
        this.contracts = contracts;
        this.contractService = contractService;
        this.paymentService = paymentService;
        this.proposals = proposals;
    }

    @Transactional
    public AcceptanceResult accept(Long proposalId) {
        Proposal proposal = proposals.findById(proposalId)
                .orElseThrow(() -> new NoSuchElementException("Proposal not found: " + proposalId));
        // The State object decides: only SENT and NEGOTIATING accept the ACCEPT event (422 otherwise).
        if (!proposal.getState().canTransitionTo(ProposalStatus.ACCEPTED)) {
            throw new InvalidStateTransitionException(proposal.getStatus(), ProposalEvent.ACCEPT,
                    ProposalStatus.ACCEPTED, proposal.getState().allowedTransitions());
        }
        if (contracts.findByProposalId(proposalId).isEmpty()) {
            contractService.generateDraft(proposalId, List.of());
        }
        Contract contract = contractService.acceptAndIssue(proposalId);
        Payment payment = paymentService.recordSimulatedDeposit(proposal, contract);
        return AcceptanceResult.from(proposal, contract, payment);
    }

    /** Result of a committed acceptance; the payment fields identify a demo receipt. */
    public record AcceptanceResult(Long proposalId, ProposalStatus proposalStatus, Long contractId,
            ContractStatus contractStatus, Long paymentId, BigDecimal depositAmount, String providerRef,
            boolean simulated) {

        public static AcceptanceResult from(Proposal proposal, Contract contract, Payment payment) {
            return new AcceptanceResult(proposal.getId(), proposal.getStatus(), contract.getId(),
                    contract.getStatus(), payment.getId(), payment.getAmount(), payment.getProviderRef(),
                    payment.isSimulated());
        }
    }
}
