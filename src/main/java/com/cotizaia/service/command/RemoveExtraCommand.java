package com.cotizaia.service.command;

import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalExtra;
import java.math.BigDecimal;

/**
 * Reversible extra removal. The command locates its row by the persisted extra
 * id and undo re-applies the exact stored delta, so the recomputed total is
 * byte-identical to the one before the removal.
 */
public class RemoveExtraCommand implements ProposalCommand {

    public static final String TYPE = "REMOVE_EXTRA";

    private final long extraId;

    private final String extraType;

    private final BigDecimal percentOrFixed;

    private final BigDecimal amount;

    public RemoveExtraCommand(long extraId, String extraType, BigDecimal percentOrFixed, BigDecimal amount) {
        if (extraType == null || extraType.isBlank() || percentOrFixed == null || amount == null) {
            throw new IllegalArgumentException("extra type, rate and amount are required");
        }
        this.extraId = extraId;
        this.extraType = extraType;
        this.percentOrFixed = percentOrFixed;
        this.amount = amount;
    }

    @Override
    public void execute(Proposal proposal) {
        ProposalExtra extra = proposal.getAppliedExtras().stream()
                .filter(candidate -> extraId == candidate.getId())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Extra " + extraId + " is gone"));
        proposal.removeExtra(extra);
    }

    @Override
    public void undo(Proposal proposal) {
        proposal.addExtra(extraType, percentOrFixed, amount);
    }

    @Override
    public String description() {
        return "Extra " + extraType + " eliminado: " + amount.toPlainString();
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String payloadJson() {
        return "{\"extraId\":" + extraId
                + ",\"type\":\"" + extraType
                + "\",\"percentOrFixed\":\"" + percentOrFixed.toPlainString()
                + "\",\"amount\":\"" + amount.toPlainString() + "\"}";
    }

    public long getExtraId() {
        return extraId;
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
