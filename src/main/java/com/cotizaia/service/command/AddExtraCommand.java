package com.cotizaia.service.command;

import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalExtra;
import java.math.BigDecimal;

/**
 * Reversible extra application. The decorator's delta is computed once by the
 * service (over the rebuilt decorator chain) and persisted here, so undo only
 * has to drop the row: {@link Proposal} re-derives the total from the remaining
 * signed amounts.
 */
public class AddExtraCommand implements ProposalCommand {

    public static final String TYPE = "ADD_EXTRA";

    private final String extraType;

    private final BigDecimal percentOrFixed;

    private final BigDecimal amount;

    public AddExtraCommand(String extraType, BigDecimal percentOrFixed, BigDecimal amount) {
        if (extraType == null || extraType.isBlank() || percentOrFixed == null || amount == null) {
            throw new IllegalArgumentException("extra type, rate and amount are required");
        }
        this.extraType = extraType;
        this.percentOrFixed = percentOrFixed;
        this.amount = amount;
    }

    @Override
    public void execute(Proposal proposal) {
        proposal.addExtra(extraType, percentOrFixed, amount);
    }

    @Override
    public void undo(Proposal proposal) {
        ProposalExtra extra = proposal.getAppliedExtras().stream()
                .filter(candidate -> extraType.equals(candidate.getType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Extra " + extraType + " is gone"));
        proposal.removeExtra(extra);
    }

    @Override
    public String description() {
        return "Extra " + extraType + ": " + amount.toPlainString();
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String payloadJson() {
        return "{\"type\":\"" + extraType
                + "\",\"percentOrFixed\":\"" + percentOrFixed.toPlainString()
                + "\",\"amount\":\"" + amount.toPlainString() + "\"}";
    }

    public String getExtraType() {
        return extraType;
    }

    public BigDecimal getPercentOrFixed() {
        return percentOrFixed;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
