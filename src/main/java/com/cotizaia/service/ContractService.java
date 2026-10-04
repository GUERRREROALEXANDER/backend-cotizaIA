package com.cotizaia.service;

import com.cotizaia.domain.Contract;
import com.cotizaia.domain.DefaultContractClauses;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.repository.ContractRepository;
import com.cotizaia.repository.ProposalRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application wrapper around the {@link Contract} aggregate (project.txt
 * section 4 acceptance flow). It keeps repository access out of the domain: the
 * aggregate owns the DRAFT -&gt; ISSUED rule, while this service loads the
 * proposal, coordinates the accept-and-issue use case and persists.
 */
@Service
public class ContractService {

    private final ContractRepository contractRepository;
    private final ProposalRepository proposalRepository;

    public ContractService(ContractRepository contractRepository, ProposalRepository proposalRepository) {
        this.contractRepository = contractRepository;
        this.proposalRepository = proposalRepository;
    }

    /**
     * Generates the draft contract that is born together with the proposal, so
     * a draft always exists before any acceptance. When no clause specs are
     * given it seeds the standard commercial clauses; the agent pipeline can
     * pass its own generated set instead.
     */
    @Transactional
    public Contract generateDraft(Long proposalId, List<ClauseSpec> clauses) {
        if (contractRepository.findByProposalId(proposalId).isPresent()) {
            throw new IllegalStateException("Contract already exists for proposal: " + proposalId);
        }
        Proposal proposal = proposalRepository
                .findById(proposalId)
                .orElseThrow(() -> new NoSuchElementException("Proposal not found: " + proposalId));
        Contract contract = Contract.draftFor(proposal);
        if (clauses == null || clauses.isEmpty()) {
            for (String text : DefaultContractClauses.DEFAULT_CLAUSES) {
                contract.addClause(text);
            }
        } else {
            for (ClauseSpec spec : clauses) {
                contract.addClause(spec.text());
            }
        }
        return contractRepository.saveAndFlush(contract);
    }

    /**
     * Accept-and-issue use case (acceptance criterion of issue #11): flips the
     * draft to ISSUED and advances the proposal to CONTRACT_ISSUED in a single
     * transaction, so both flips commit or roll back together.
     *
     * <p>The proposal's legal move is checked first and the contract validates
     * its own DRAFT -&gt; ISSUED move, so a rejected acceptance leaves the
     * contract as a clean draft rather than a half-issued one. (Payment is its
     * own aggregate in issue #12 and plays no role here.)
     */
    @Transactional
    public Contract acceptAndIssue(Long proposalId) {
        Proposal proposal = proposalRepository
                .findById(proposalId)
                .orElseThrow(() -> new NoSuchElementException("Proposal not found: " + proposalId));
        Contract contract = contractRepository
                .findByProposalId(proposalId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Contract not found for proposal: " + proposalId));

        ProposalStatus status = proposal.getStatus();
        if (status != ProposalStatus.SENT
                && status != ProposalStatus.NEGOTIATING
                && status != ProposalStatus.ACCEPTED) {
            throw new IllegalStateException("Proposal cannot be accepted from " + status);
        }

        contract.issue();
        if (status != ProposalStatus.ACCEPTED) {
            proposal.transitionTo(ProposalStatus.ACCEPTED);
        }
        proposal.transitionTo(ProposalStatus.CONTRACT_ISSUED);

        proposalRepository.saveAndFlush(proposal);
        return contractRepository.saveAndFlush(contract);
    }

    @Transactional(readOnly = true)
    public Contract requireByProposal(Long proposalId) {
        return contractRepository
                .findByProposalId(proposalId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Contract not found for proposal: " + proposalId));
    }

    /** Input for one clause when generating a draft. */
    public record ClauseSpec(String text) {
    }
}
