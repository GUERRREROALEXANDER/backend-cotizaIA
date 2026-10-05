package com.cotizaia.api;

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
import java.util.List;
import java.util.Set;

/**
 * Projects the loaded proposal review view (project.txt sections 2 and 5).
 * Explicit nested records expose business values without serializing JPA associations or PDF content.
 */
public record ProposalDetailResponse(Long id, Long briefId, ProposalStatus status,
        Set<ProposalStatus> allowedTransitions, BigDecimal subtotal, BigDecimal total, PricingModel pricingModel,
        Instant approvedAt, List<ItemResponse> items, List<ExtraResponse> extras, ScheduleResponse schedule,
        List<StatusChangeResponse> statusHistory, List<DocumentResponse> documents, DepositResponse deposit) {

    public static ProposalDetailResponse from(Detail detail) {
        Proposal proposal = detail.proposal();
        return new ProposalDetailResponse(proposal.getId(), proposal.getBrief().getId(), proposal.getStatus(),
                proposal.getState().allowedTransitions(), proposal.getSubtotal(), proposal.getTotal(),
                proposal.getPricingModel(), proposal.getApprovedAt(),
                proposal.getItems().stream().map(ItemResponse::from).toList(),
                proposal.getAppliedExtras().stream().map(ExtraResponse::from).toList(),
                ScheduleResponse.from(proposal.getSchedulePlan()),
                proposal.getStatusHistory().stream().map(StatusChangeResponse::from).toList(),
                detail.documents().stream().map(DocumentResponse::from).toList(),
                DepositResponse.from(detail.deposit()));
    }

    /** One priced requirement with its current reviewed hours. */
    public record ItemResponse(Long id, Long requirementId, String description, BigDecimal hours,
            BigDecimal unitPrice, BigDecimal lineTotal) {

        public static ItemResponse from(QuotedItem item) {
            return new ItemResponse(item.getId(), item.getRequirement().getId(),
                    item.getRequirement().getDescription(), item.getHours(), item.getUnitPrice(), item.getLineTotal());
        }
    }

    /** One persisted adjustment to the base quotation. */
    public record ExtraResponse(Long id, String type, BigDecimal percentOrFixed, BigDecimal amount) {

        public static ExtraResponse from(ProposalExtra extra) {
            return new ExtraResponse(extra.getId(), extra.getType(), extra.getPercentOrFixed(), extra.getAmount());
        }
    }

    /** Recalculated capacity and phases. */
    public record ScheduleResponse(BigDecimal hoursPerWeek, BigDecimal totalHours, List<PhaseResponse> phases) {

        public static ScheduleResponse from(Schedule schedule) {
            return schedule == null ? null : new ScheduleResponse(schedule.getHoursPerWeek(), schedule.getTotalHours(),
                    schedule.getPhases().stream().map(PhaseResponse::from).toList());
        }
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
