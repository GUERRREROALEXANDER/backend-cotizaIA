package com.cotizaia.api;

import com.cotizaia.domain.ContractStatus;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.service.ProposalAcceptanceService.AcceptanceResult;
import java.math.BigDecimal;

/** Public projection without persistence internals. */
public record AcceptanceResponse(Long proposalId, ProposalStatus proposalStatus, Long contractId,
        ContractStatus contractStatus, Long paymentId, BigDecimal depositAmount, String providerRef,
        boolean simulated, boolean projectActive) {

    public static AcceptanceResponse from(AcceptanceResult result) {
        // "Proyecto activado" (Spec 15): a successful accept issues the contract
        // and records the deposit, so the derived flag is true here.
        boolean projectActive = result.contractStatus() == ContractStatus.ISSUED
                && result.paymentId() != null;
        return new AcceptanceResponse(result.proposalId(), result.proposalStatus(), result.contractId(),
                result.contractStatus(), result.paymentId(), result.depositAmount(), result.providerRef(),
                result.simulated(), projectActive);
    }
}
