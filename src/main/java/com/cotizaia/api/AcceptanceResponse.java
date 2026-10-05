package com.cotizaia.api;

import com.cotizaia.domain.ContractStatus;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.service.ProposalAcceptanceService.AcceptanceResult;
import java.math.BigDecimal;

/** Public projection without persistence internals. */
public record AcceptanceResponse(Long proposalId, ProposalStatus proposalStatus, Long contractId,
        ContractStatus contractStatus, Long paymentId, BigDecimal depositAmount, String providerRef, boolean simulated) {

    public static AcceptanceResponse from(AcceptanceResult result) {
        return new AcceptanceResponse(result.proposalId(), result.proposalStatus(), result.contractId(),
                result.contractStatus(), result.paymentId(), result.depositAmount(), result.providerRef(),
                result.simulated());
    }
}
