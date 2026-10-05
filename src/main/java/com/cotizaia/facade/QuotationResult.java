package com.cotizaia.facade;

import com.cotizaia.document.DocumentType;
import com.cotizaia.domain.AgentExecution;
import com.cotizaia.domain.BriefQuestion;
import com.cotizaia.domain.ExecutionStatus;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.domain.QuestionStatus;
import com.cotizaia.pricing.PricingModel;
import java.math.BigDecimal;
import java.util.List;

/**
 * Exposes a completed quotation attempt (project.txt section 2) without lazy entity associations.
 * Execution and proposal outcomes remain distinct because a successful analysis can halt for clarification.
 */
public record QuotationResult(Long briefId, Long proposalId, ProposalStatus proposalStatus,
        Long executionId, ExecutionStatus executionStatus, boolean halted, BigDecimal subtotal,
        BigDecimal total, PricingModel pricingModel, List<DocumentType> documents, List<QuestionView> questions) {

    public QuotationResult {
        documents = List.copyOf(documents);
        questions = List.copyOf(questions);
    }

    public static QuotationResult from(Proposal proposal, AgentExecution execution, boolean halted,
            List<DocumentType> documents, List<BriefQuestion> questions) {
        return new QuotationResult(proposal.getBrief().getId(), proposal.getId(), proposal.getStatus(),
                execution.getId(), execution.getStatus(), halted, proposal.getSubtotal(), proposal.getTotal(),
                proposal.getPricingModel(), documents, questions.stream().map(QuestionView::from).toList());
    }

    /** Exposes a persisted clarification for review or resolution. */
    public record QuestionView(Long id, String code, String question, boolean blocking, QuestionStatus status) {

        public static QuestionView from(BriefQuestion question) {
            return new QuestionView(question.getId(), question.getCode(), question.getQuestion(),
                    question.isBlocking(), question.getStatus());
        }
    }
}
