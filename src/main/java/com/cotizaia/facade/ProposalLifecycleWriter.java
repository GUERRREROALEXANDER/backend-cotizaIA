package com.cotizaia.facade;

import com.cotizaia.agent.AgentRun;
import com.cotizaia.agent.pipeline.PipelineContext;
import com.cotizaia.agent.pipeline.PipelineProperties;
import com.cotizaia.agent.pipeline.QuoteDraft;
import com.cotizaia.agent.pipeline.RequirementDraft;
import com.cotizaia.document.DocumentType;
import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefQuestion;
import com.cotizaia.domain.ExtractedRequirement;
import com.cotizaia.domain.Phase;
import com.cotizaia.domain.PhaseDependency;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.domain.Schedule;
import com.cotizaia.domain.state.ProposalEvent;
import com.cotizaia.pricing.PricedLine;
import com.cotizaia.repository.BriefRepository;
import com.cotizaia.repository.ContractRepository;
import com.cotizaia.repository.ExtractedRequirementRepository;
import com.cotizaia.repository.ProposalRepository;
import com.cotizaia.service.BriefQuestionService;
import com.cotizaia.service.ContractService;
import com.cotizaia.service.DocumentService;
import com.cotizaia.service.ProposalWorkflowService;
import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Commits the facade's business changes in short transactions (project.txt sections 2 and 6).
 * Opening analysis is durable before the chain starts; replacing a quote, its questions,
 * schedule, contract, PDFs and notifications either commits together or rolls back together.
 * The run recorder has a separate transaction, so business rollback never erases the agent log.
 */
@Service
public class ProposalLifecycleWriter {

    private final ProposalRepository proposals;

    private final BriefRepository briefs;

    private final ExtractedRequirementRepository requirements;

    private final ContractRepository contracts;

    private final BriefQuestionService questions;

    private final ProposalWorkflowService workflow;

    private final ContractService contractService;

    private final DocumentService documents;

    private final PipelineProperties properties;

    public ProposalLifecycleWriter(ProposalRepository proposals, BriefRepository briefs,
            ExtractedRequirementRepository requirements, ContractRepository contracts,
            BriefQuestionService questions, ProposalWorkflowService workflow,
            ContractService contractService, DocumentService documents, PipelineProperties properties) {
        this.proposals = proposals;
        this.briefs = briefs;
        this.requirements = requirements;
        this.contracts = contracts;
        this.questions = questions;
        this.workflow = workflow;
        this.contractService = contractService;
        this.documents = documents;
        this.properties = properties;
    }

    @Transactional
    public Long openForAnalysis(Long briefId) {
        Brief brief = briefs.findById(briefId)
                .orElseThrow(() -> new NoSuchElementException("Brief not found: " + briefId));
        Proposal proposal = proposals.findByBriefIdOrderByIdDesc(briefId).stream()
                .filter(candidate -> !candidate.getState().isTerminal()).findFirst()
                .orElseGet(() -> proposals.save(new Proposal.Builder().brief(brief).build()));
        requireAnalyzable(proposal);
        if (proposal.getStatus() == ProposalStatus.RECEIVED) {
            workflow.transition(proposal.getId(), ProposalEvent.START_ANALYSIS);
        }
        return proposal.getId();
    }

    @Transactional
    public QuotationResult applyOutcome(Long proposalId, AgentRun run) {
        Proposal proposal = proposals.findById(proposalId)
                .orElseThrow(() -> new NoSuchElementException("Proposal not found: " + proposalId));
        requireAnalyzable(proposal);
        PipelineContext context = (PipelineContext) run.result();
        if (!proposal.getBrief().getId().equals(context.getBrief().getId())) {
            throw new IllegalArgumentException("Pipeline run belongs to another brief");
        }
        List<BriefQuestion> synced = questions.sync(proposal.getBrief(), context.getFindings().stream()
                .map(finding -> new BriefQuestionService.QuestionSpec(
                        finding.code(), finding.question(), finding.blocking())).toList());
        if (context.isHalted()) {
            return QuotationResult.from(proposal, run.execution(), true, List.of(), synced);
        }
        QuoteDraft draft = context.getDraft();
        clearScheduleDependencies(proposal);
        proposal.clearQuote();
        proposals.flush();
        requirements.deleteAll(requirements.findUnreferencedByBriefId(proposal.getBrief().getId()));
        for (RequirementDraft requirement : draft.requirements()) {
            PricedLine line = draft.quote().lines().stream()
                    .filter(candidate -> candidate.key().equals(requirement.key())).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Missing priced requirement: " + requirement.key()));
            ExtractedRequirement extracted = requirements.save(new ExtractedRequirement(proposal.getBrief(),
                    requirement.getRequirementType(), requirement.getDescription(), requirement.getEstimatedHours(),
                    requirement.getConfidence()));
            proposal.addItem(extracted, requirement.getEstimatedHours(), line.unitPrice());
        }
        proposal.applyPricingModel(draft.quote().model());
        attachSchedule(proposal, draft);
        proposals.saveAndFlush(proposal);
        if (contracts.findByProposalId(proposalId).isEmpty()) {
            contractService.generateDraft(proposalId, List.of());
        }
        documents.store(proposal, draft.documents());
        if (proposal.getStatus() == ProposalStatus.ANALYZING) {
            workflow.transition(proposalId, ProposalEvent.QUOTE);
        }
        if (proposal.getStatus() == ProposalStatus.QUOTED) {
            workflow.transition(proposalId, ProposalEvent.SUBMIT_FOR_REVIEW);
        }
        return QuotationResult.from(proposal, run.execution(), false, List.of(DocumentType.values()), synced);
    }

    private void clearScheduleDependencies(Proposal proposal) {
        Schedule schedule = proposal.getSchedulePlan();
        if (schedule == null) {
            return;
        }
        for (Phase phase : schedule.getPhases()) {
            for (PhaseDependency dependency : List.copyOf(phase.getDependencies())) {
                schedule.removeDependency(dependency);
            }
        }
        // Delete edges before phases so database cascades cannot delete an edge still managed by Hibernate.
        proposals.flush();
    }

    private void attachSchedule(Proposal proposal, QuoteDraft draft) {
        Schedule schedule = proposal.attachSchedule(BigDecimal.valueOf(properties.getHoursPerWeek()));
        List<BigDecimal> shares = List.of(new BigDecimal("0.20"), new BigDecimal("0.50"),
                new BigDecimal("0.15"), new BigDecimal("0.15"));
        if (draft.phasePlan().size() != shares.size()) {
            throw new IllegalArgumentException("Expected four draft phases");
        }
        Phase previous = null;
        for (int index = 0; index < shares.size(); index++) {
            Phase phase = schedule.addPhase(draft.phasePlan().get(index).name(), shares.get(index));
            if (previous != null) {
                schedule.addDependency(phase, previous);
            }
            previous = phase;
        }
    }

    private void requireAnalyzable(Proposal proposal) {
        if (proposal.getStatus() != ProposalStatus.RECEIVED && proposal.getStatus() != ProposalStatus.ANALYZING
                && proposal.getStatus() != ProposalStatus.QUOTED && proposal.getStatus() != ProposalStatus.IN_REVIEW) {
            throw new IllegalStateException("Proposal " + proposal.getId()
                    + " was already sent; it cannot be re-analyzed");
        }
    }

}
