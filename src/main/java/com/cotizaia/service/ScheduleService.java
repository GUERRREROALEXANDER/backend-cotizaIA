package com.cotizaia.service;

import com.cotizaia.domain.Phase;
import com.cotizaia.domain.PhaseDependency;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.QuotedItem;
import com.cotizaia.domain.Schedule;
import com.cotizaia.repository.ProposalRepository;
import com.cotizaia.repository.ScheduleRepository;
import java.math.BigDecimal;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application wrapper around the {@link Schedule} aggregate
 * (project.txt section 5 "Cronograma inteligente"). It keeps the methods that
 * need repository access out of the domain: the aggregate itself owns the
 * cascade maths and the cycle rule, while this service loads the proposal,
 * delegates, and persists.
 *
 * <p>The hour edit is the load-bearing use case: {@link #editHours} changes one
 * {@link QuotedItem}'s hours on the proposal, and the aggregate recalculates the
 * whole schedule in cascade; because both live in one transaction, the proposal
 * and its shifted phases commit together.
 */
@Service
public class ScheduleService {

    private final ProposalRepository proposalRepository;
    private final ScheduleRepository scheduleRepository;

    public ScheduleService(ProposalRepository proposalRepository, ScheduleRepository scheduleRepository) {
        this.proposalRepository = proposalRepository;
        this.scheduleRepository = scheduleRepository;
    }

    /**
     * Builds a schedule for the proposal with the given phases (name + share)
     * and weekly capacity. Shares are validated by {@link Phase}; the aggregate
     * places the timeline as each phase is added.
     */
    @Transactional
    public Schedule createFor(Long proposalId, BigDecimal hoursPerWeek, Iterable<PhaseSpec> phaseSpecs) {
        Proposal proposal = proposalRepository
                .findById(proposalId)
                .orElseThrow(() -> new NoSuchElementException("Proposal not found: " + proposalId));
        Schedule schedule = proposal.attachSchedule(hoursPerWeek);
        if (phaseSpecs != null) {
            for (PhaseSpec spec : phaseSpecs) {
                schedule.addPhase(spec.name(), spec.hoursShare());
            }
        }
        return scheduleRepository.saveAndFlush(schedule);
    }

    @Transactional(readOnly = true)
    public Schedule requireByProposal(Long proposalId) {
        return scheduleRepository
                .findByProposalId(proposalId)
                .orElseThrow(() -> new NoSuchElementException("Schedule not found for proposal: " + proposalId));
    }

    /**
     * Human hour adjustment at approval time (project.txt section 5). Delegates
     * to {@link Proposal#editHours}, which reconciles the totals and cascades
     * the change through the schedule; the proposal is saved so the shifted
     * phases persist atomically with the new hours.
     */
    @Transactional
    public Proposal editHours(Long proposalId, Long quotedItemId, BigDecimal newHours) {
        Proposal proposal = proposalRepository
                .findById(proposalId)
                .orElseThrow(() -> new NoSuchElementException("Proposal not found: " + proposalId));
        QuotedItem item = proposal.getItems().stream()
                .filter(candidate -> candidate.getId().equals(quotedItemId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Quoted item not found: " + quotedItemId));
        proposal.editHours(item, newHours);
        return proposalRepository.saveAndFlush(proposal);
    }

    /**
     * Links two phases finish-to-start. The cycle rule lives in
     * {@link Schedule#addDependency}, so an edge that closes a loop throws and
     * nothing is persisted.
     */
    @Transactional
    public PhaseDependency addDependency(Long proposalId, Long dependentPhaseId, Long prerequisitePhaseId) {
        Schedule schedule = requireByProposal(proposalId);
        Phase dependent = findPhase(schedule, dependentPhaseId);
        Phase prerequisite = findPhase(schedule, prerequisitePhaseId);
        PhaseDependency edge = schedule.addDependency(dependent, prerequisite);
        scheduleRepository.saveAndFlush(schedule);
        return edge;
    }

    private Phase findPhase(Schedule schedule, Long phaseId) {
        return schedule.getPhases().stream()
                .filter(phase -> phase.getId().equals(phaseId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Phase not found: " + phaseId));
    }

    /** Input for one phase when creating a schedule. */
    public record PhaseSpec(String name, BigDecimal hoursShare) {
    }
}
