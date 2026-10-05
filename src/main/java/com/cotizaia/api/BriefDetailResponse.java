package com.cotizaia.api;

import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.domain.BriefQuestion;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.domain.QuestionStatus;
import com.cotizaia.service.BriefDetailService.Detail;
import java.time.Instant;
import java.util.List;

/** Intake, clarification answers and references to the latest quotation and executions. */
public record BriefDetailResponse(Long id, Long clientId, BriefChannel source, String rawText, String rawPayload,
        Instant receivedAt, List<QuestionResponse> questions, Long proposalId, ProposalStatus proposalStatus,
        List<Long> executionIds) {

    public static BriefDetailResponse from(Detail detail) {
        Brief brief = detail.brief();
        return new BriefDetailResponse(brief.getId(), brief.getClient().getId(), brief.getSource(),
                brief.getRawText(), brief.getRawPayload(), brief.getReceivedAt(),
                detail.questions().stream().map(QuestionResponse::from).toList(),
                detail.latest() == null ? null : detail.latest().getId(),
                detail.latest() == null ? null : detail.latest().getStatus(), detail.executionIds());
    }

    /** A durable question and its optional answer. */
    public record QuestionResponse(Long id, String code, String question, boolean blocking,
            QuestionStatus status, String answer, Instant resolvedAt) {

        public static QuestionResponse from(BriefQuestion question) {
            return new QuestionResponse(question.getId(), question.getCode(), question.getQuestion(),
                    question.isBlocking(), question.getStatus(), question.getAnswer(), question.getResolvedAt());
        }
    }
}
