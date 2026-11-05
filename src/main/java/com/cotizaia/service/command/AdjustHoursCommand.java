package com.cotizaia.service.command;

import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.QuotedItem;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Reversible hour edit. Quoted lines are immutable and replaced on every edit,
 * so the command locates its line by the stable requirement id instead of the
 * item id, which changes with each replace.
 */
public class AdjustHoursCommand implements ProposalCommand {

    public static final String TYPE = "ADJUST_HOURS";

    private final long requirementId;

    private final BigDecimal before;

    private final BigDecimal after;

    private String requirementDescription;

    public AdjustHoursCommand(long requirementId, BigDecimal before, BigDecimal after) {
        if (before == null || after == null) {
            throw new IllegalArgumentException("before and after hours are required");
        }
        this.requirementId = requirementId;
        this.before = before;
        this.after = after;
    }

    @Override
    public void execute(Proposal proposal) {
        edit(proposal, after);
    }

    @Override
    public void undo(Proposal proposal) {
        edit(proposal, before);
    }

    private void edit(Proposal proposal, BigDecimal hours) {
        QuotedItem item = proposal.getItems().stream()
                .filter(candidate -> Objects.equals(candidate.getRequirement().getId(), requirementId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Quoted line for requirement " + requirementId + " is gone"));
        requirementDescription = item.getRequirement().getDescription();
        proposal.editHours(item, hours);
    }

    @Override
    public String description() {
        String label = requirementDescription == null || requirementDescription.isBlank()
                ? "requisito " + requirementId : requirementDescription;
        return "Horas de " + label + ": " + before.toPlainString() + " -> " + after.toPlainString();
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String payloadJson() {
        return "{\"requirementId\":" + requirementId
                + ",\"before\":" + before.toPlainString()
                + ",\"after\":" + after.toPlainString() + "}";
    }

    public long getRequirementId() {
        return requirementId;
    }

    public BigDecimal getBefore() {
        return before;
    }

    public BigDecimal getAfter() {
        return after;
    }
}
