package com.cotizaia.document;

import com.cotizaia.domain.Phase;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalExtra;
import com.cotizaia.domain.QuotedItem;
import com.cotizaia.pricing.PricingModel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Immutable PDF input (project.txt section 6): a snapshot allows the agent pipeline and persisted proposals
 * to use the same generators without making PDF rendering depend on JPA state.
 */
public record DocumentData(
        String agencyName, String clientName, String reference, LocalDate issuedOn, String summary,
        PricingModel pricingModel, List<LineData> lines, BigDecimal subtotal, List<ExtraData> extras,
        BigDecimal total, BigDecimal depositAmount, List<PhaseData> phases, List<String> clauses) {

    public DocumentData {
        lines = List.copyOf(lines);
        extras = List.copyOf(extras);
        phases = List.copyOf(phases);
        clauses = List.copyOf(clauses);
    }

    public static DocumentData from(Proposal proposal, List<String> clauses, String summary) {
        List<LineData> lines = proposal.getItems().stream().map(DocumentData::line).toList();
        List<ExtraData> extras = proposal.getAppliedExtras().stream().map(DocumentData::extra).toList();
        List<PhaseData> phases = proposal.getSchedulePlan() == null ? List.of()
                : proposal.getSchedulePlan().getPhases().stream().map(DocumentData::phase).toList();
        return new DocumentData(proposal.getBrief().getClient().getAgency().getName(),
                proposal.getBrief().getClient().getName(), "Brief #" + proposal.getBrief().getId(),
                LocalDate.now(), summary, proposal.getPricingModel(), lines, proposal.getSubtotal(), extras,
                proposal.getTotal(), proposal.getTotal().multiply(new BigDecimal("0.50"))
                        .setScale(2, RoundingMode.HALF_UP), phases, clauses);
    }

    private static LineData line(QuotedItem item) {
        return new LineData(item.getRequirement().getDescription(), item.getHours(), item.getUnitPrice(),
                item.getLineTotal());
    }

    private static ExtraData extra(ProposalExtra extra) {
        return new ExtraData(extra.getType(), extra.getAmount());
    }

    private static PhaseData phase(Phase phase) {
        return new PhaseData(phase.getName(), phase.getWeeks(), phase.getStartWeek(), phase.getEndWeek());
    }

    /** One priced requirement detached from its entity. */
    public record LineData(String label, BigDecimal hours, BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    /** One applied price adjustment detached from its entity. */
    public record ExtraData(String type, BigDecimal amount) {
    }

    /** One scheduled phase detached from its entity. */
    public record PhaseData(String name, int weeks, int startWeek, int endWeek) {
    }
}
