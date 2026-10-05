package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BriefQuestionDomainTests {

    @Test
    void opensRefreshesAndResolvesWithPairedEvidence() {
        BriefQuestion question = BriefQuestion.open(BriefFixture.brief(), "SCOPE", "What scope?", true);
        assertThat(question.getStatus()).isEqualTo(QuestionStatus.OPEN);
        assertThat(question.getAnswer()).isNull();
        assertThat(question.getResolvedAt()).isNull();
        question.refresh("How many pages?", false);
        assertThat(question.getQuestion()).isEqualTo("How many pages?");
        assertThat(question.isBlocking()).isFalse();
        Instant at = Instant.parse("2026-10-04T12:00:00Z");
        question.resolve("Three", at);
        assertThat(question.getStatus()).isEqualTo(QuestionStatus.RESOLVED);
        assertThat(question.getAnswer()).isEqualTo("Three");
        assertThat(question.getResolvedAt()).isEqualTo(at);
        assertThatThrownBy(() -> question.resolve("Four", at)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> question.refresh("Other?", true)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invalidInputNeverPartiallyResolves() {
        BriefQuestion question = BriefQuestion.open(BriefFixture.brief(), "SCOPE", "What scope?", true);
        for (String answer : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> question.resolve(answer, Instant.now()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> question.resolve("Three", null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(question.getStatus()).isEqualTo(QuestionStatus.OPEN);
        assertThat(question.getAnswer()).isNull();
        assertThat(question.getResolvedAt()).isNull();
        assertThatThrownBy(() -> BriefQuestion.open(null, "SCOPE", "Scope?", false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BriefQuestion.open(BriefFixture.brief(), " ", "Scope?", false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BriefQuestion.open(BriefFixture.brief(), "X".repeat(61), "Scope?", false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> question.refresh("X".repeat(1001), true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void approvalRequiresReviewAndQuoteChangesInvalidateIt() {
        Proposal proposal = new Proposal.Builder().brief(BriefFixture.brief()).build();
        assertThatThrownBy(() -> proposal.approve(Instant.now())).isInstanceOf(IllegalStateException.class);
        proposal.transitionTo(ProposalStatus.ANALYZING);
        proposal.transitionTo(ProposalStatus.QUOTED);
        proposal.transitionTo(ProposalStatus.IN_REVIEW);
        assertThatThrownBy(() -> proposal.approve(null)).isInstanceOf(IllegalArgumentException.class);
        proposal.approve(Instant.now());
        assertThat(proposal.isApproved()).isTrue();
        proposal.clearQuote();
        assertThat(proposal.isApproved()).isFalse();
        assertThat(proposal.getApprovedAt()).isNull();
        assertThat(proposal.getTotal()).isZero();
    }
}
