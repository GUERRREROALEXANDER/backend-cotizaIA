package com.cotizaia.api;

import com.cotizaia.agent.pipeline.Citation;
import com.cotizaia.agent.pipeline.EstimateSource;
import com.cotizaia.document.ProposalDraft;
import com.cotizaia.domain.ContractStatus;
import com.cotizaia.domain.ExtractedRequirement;
import com.cotizaia.domain.Payment;
import com.cotizaia.domain.Phase;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalExtra;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.domain.ProposalStatusChange;
import com.cotizaia.domain.QuotedItem;
import com.cotizaia.domain.Schedule;
import com.cotizaia.pricing.PricingModel;
import com.cotizaia.service.ProposalService.Detail;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Projects the loaded proposal review view (project.txt sections 2 and 5).
 * Explicit nested records expose business values without serializing JPA associations or PDF content.
 */
public record ProposalDetailResponse(Long id, Long briefId, ProposalStatus status, int revisionNumber,
        Long revisionOf, Set<ProposalStatus> allowedTransitions, BigDecimal subtotal, BigDecimal total,
        PricingModel pricingModel, Instant approvedAt, Instant viewedAt, List<ItemResponse> items,
        List<ExtraResponse> extras, ScheduleResponse schedule, List<StatusChangeResponse> statusHistory,
        List<DocumentResponse> documents, DepositResponse deposit, ProposalDraft draft, boolean projectActive) {

    public static ProposalDetailResponse from(Detail detail) {
        Proposal proposal = detail.proposal();
        // "Proyecto activado" (Spec 15): derived, never stored. The project is
        // active only once its contract is ISSUED and a deposit payment exists.
        boolean projectActive = detail.deposit() != null
                && detail.deposit().getContract().getStatus() == ContractStatus.ISSUED;
        return new ProposalDetailResponse(proposal.getId(), proposal.getBrief().getId(), proposal.getStatus(),
                proposal.getRevisionNumber(),
                proposal.getRevisionOf() == null ? null : proposal.getRevisionOf().getId(),
                proposal.getState().allowedTransitions(), proposal.getSubtotal(), proposal.getTotal(),
                proposal.getPricingModel(), proposal.getApprovedAt(), proposal.getViewedAt(),
                proposal.getItems().stream().map(ItemResponse::from).toList(),
                proposal.getAppliedExtras().stream().map(ExtraResponse::from).toList(),
                ScheduleResponse.from(proposal.getSchedulePlan()),
                proposal.getStatusHistory().stream().map(StatusChangeResponse::from).toList(),
                detail.documents().stream().map(DocumentResponse::from).toList(),
                DepositResponse.from(detail.deposit()), draft(proposal), projectActive);
    }

    private static ProposalDraft draft(Proposal proposal) {
        try {
            return ProposalDraft.fromJson(proposal.getDraftedContent());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** One priced requirement with its current reviewed hours and estimation provenance. */
    public record ItemResponse(Long id, Long requirementId, String description, String requirementType,
            EstimateSource estimateSource, List<Citation> citations, BigDecimal hours, BigDecimal unitPrice,
            BigDecimal lineTotal) {

        public static ItemResponse from(QuotedItem item) {
            ExtractedRequirement requirement = item.getRequirement();
            return new ItemResponse(item.getId(), requirement.getId(), requirement.getDescription(),
                    requirement.getRequirementType().getName(),
                    EstimateSource.fromStored(requirement.getEstimateSource()),
                    Citation.fromJson(requirement.getCitations()), item.getHours(), item.getUnitPrice(),
                    item.getLineTotal());
        }
    }

    /** One persisted adjustment to the base quotation. */
    public record ExtraResponse(Long id, String type, BigDecimal percentOrFixed, BigDecimal amount) {

        public static ExtraResponse from(ProposalExtra extra) {
            return new ExtraResponse(extra.getId(), extra.getType(), extra.getPercentOrFixed(), extra.getAmount());
        }
    }

    /** Recalculated capacity, phases and the derived delivery/launch milestones. */
    public record ScheduleResponse(BigDecimal hoursPerWeek, BigDecimal totalHours, List<PhaseResponse> phases,
            List<MilestoneResponse> milestones) {

        /**
         * Pure derivation of the schedule milestones (Spec 15): one "Entrega"
         * per phase at that phase's end week, plus the final "Lanzamiento" at
         * the last (latest) end week of the project. Nothing is persisted.
         */
        public static ScheduleResponse from(Schedule schedule) {
            if (schedule == null) {
                return null;
            }
            List<PhaseResponse> phases = schedule.getPhases().stream().map(PhaseResponse::from).toList();
            List<MilestoneResponse> milestones = new ArrayList<>();
            int lastEndWeek = 0;
            for (Phase phase : schedule.getPhases()) {
                milestones.add(new MilestoneResponse("Entrega: " + phase.getName(), phase.getEndWeek()));
                lastEndWeek = Math.max(lastEndWeek, phase.getEndWeek());
            }
            milestones.add(new MilestoneResponse("Lanzamiento", lastEndWeek));
            return new ScheduleResponse(schedule.getHoursPerWeek(), schedule.getTotalHours(), phases, milestones);
        }
    }

    /** One derived milestone in the project timeline. */
    public record MilestoneResponse(String name, int week) {
    }

    /** One phase in the dependency-aware timeline. */
    public record PhaseResponse(String name, int weeks, int startWeek, int endWeek) {

        public static PhaseResponse from(Phase phase) {
            return new PhaseResponse(phase.getName(), phase.getWeeks(), phase.getStartWeek(), phase.getEndWeek());
        }
    }

    /** Persisted lifecycle evidence. */
    public record StatusChangeResponse(ProposalStatus from, ProposalStatus to, Instant changedAt) {

        public static StatusChangeResponse from(ProposalStatusChange change) {
            return new StatusChangeResponse(change.getFromStatus(), change.getToStatus(), change.getChangedAt());
        }
    }

    /** Explicitly identifies the simulated deposit. */
    public record DepositResponse(BigDecimal amount, String providerRef, boolean simulated) {

        public static DepositResponse from(Payment payment) {
            return payment == null ? null
                    : new DepositResponse(payment.getAmount(), payment.getProviderRef(), payment.isSimulated());
        }
    }
}
