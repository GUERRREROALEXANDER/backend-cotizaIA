package com.cotizaia.api;

import com.cotizaia.document.DocumentType;
import com.cotizaia.domain.ExecutionStatus;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.facade.QuotationResult;
import com.cotizaia.facade.QuotationResult.QuestionView;
import com.cotizaia.pricing.PricingModel;
import java.math.BigDecimal;
import java.util.List;

/** Public projection without persistence internals. */
public record QuotationResponse(Long briefId, Long proposalId, ProposalStatus proposalStatus,
        Long executionId, ExecutionStatus executionStatus, boolean halted, BigDecimal subtotal,
        BigDecimal total, PricingModel pricingModel, List<DocumentType> documents, List<QuestionView> questions) {

    public static QuotationResponse from(QuotationResult result) {
        return new QuotationResponse(result.briefId(), result.proposalId(), result.proposalStatus(),
                result.executionId(), result.executionStatus(), result.halted(), result.subtotal(), result.total(),
                result.pricingModel(), result.documents(), result.questions());
    }
}
