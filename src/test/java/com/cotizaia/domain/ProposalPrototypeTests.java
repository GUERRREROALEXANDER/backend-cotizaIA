package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Prototype: a revision is a deep copy of the negotiated proposal. Nothing mutable is shared, so the human can
 * rework v2 while v1 stays exactly what the client received.
 */
class ProposalPrototypeTests {

    @Test
    void copyIsADeepRevisionThatSharesNoMutableState() {
        Proposal original = negotiated();

        Proposal revision = original.copy();

        assertThat(revision).isNotSameAs(original);
        assertThat(revision.getId()).isNull();
        assertThat(revision.getBrief()).isSameAs(original.getBrief());
        assertThat(revision.getStatus()).isEqualTo(ProposalStatus.IN_REVIEW);
        assertThat(revision.isApproved()).isFalse();
        assertThat(revision.getStatusHistory()).isEmpty();
        assertThat(revision.getRevisionNumber()).isEqualTo(original.getRevisionNumber() + 1);
        assertThat(revision.getRevisionOf()).isSameAs(original);
        assertThat(revision.getDraftedContent()).isEqualTo(original.getDraftedContent());
        assertThat(revision.getPricingModel()).isEqualTo(original.getPricingModel());
        assertThat(revision.getTotal()).isEqualByComparingTo(original.getTotal());

        assertThat(revision.getItems()).hasSameSizeAs(original.getItems());
        for (int i = 0; i < original.getItems().size(); i++) {
            assertThat(revision.getItems().get(i)).isNotSameAs(original.getItems().get(i));
            assertThat(revision.getItems().get(i).getRequirement()).isSameAs(original.getItems().get(i).getRequirement());
            assertThat(revision.getItems().get(i).getHours()).isEqualByComparingTo(original.getItems().get(i).getHours());
        }
        assertThat(revision.getAppliedExtras()).hasSize(1);
        assertThat(revision.getAppliedExtras().get(0)).isNotSameAs(original.getAppliedExtras().get(0));

        Schedule copied = revision.getSchedulePlan();
        assertThat(copied).isNotNull().isNotSameAs(original.getSchedulePlan());
        assertThat(copied.getPhases()).extracting(Phase::getName)
                .containsExactly("Design UX", "Development", "QA + launch");
        assertThat(copied.getPhases().get(1).getStartWeek())
                .isEqualTo(original.getSchedulePlan().getPhases().get(1).getStartWeek());
        assertThat(copied.getPhases()).allSatisfy(phase ->
                assertThat(original.getSchedulePlan().getPhases()).doesNotContain(phase));
    }

    @Test
    void editingTheRevisionLeavesTheOriginalUntouched() {
        Proposal original = negotiated();
        BigDecimal originalTotal = original.getTotal();
        int originalEnd = original.getSchedulePlan().getPhases().get(2).getEndWeek();

        Proposal revision = original.copy();
        revision.editHours(revision.getItems().get(0), new BigDecimal("200"));

        assertThat(original.getItems().get(0).getHours()).isEqualByComparingTo("60");
        assertThat(original.getTotal()).isEqualByComparingTo(originalTotal);
        assertThat(original.getSchedulePlan().getPhases().get(2).getEndWeek()).isEqualTo(originalEnd);
        assertThat(revision.getTotal()).isGreaterThan(originalTotal);
        assertThat(revision.getSchedulePlan().getPhases().get(2).getEndWeek()).isGreaterThan(originalEnd);
    }

    @Test
    void revisionsOfRevisionsKeepCounting() {
        Proposal v1 = negotiated();
        Proposal v2 = v1.copy();
        Proposal v3 = v2.copy();
        assertThat(v1.getRevisionNumber()).isEqualTo(1);
        assertThat(v3.getRevisionNumber()).isEqualTo(3);
        assertThat(v3.getRevisionOf()).isSameAs(v2);
    }

    private static Proposal negotiated() {
        Proposal proposal = new Proposal.Builder().brief(BriefFixture.brief()).status(ProposalStatus.IN_REVIEW).build();
        proposal.addItem(BriefFixture.requirement(), new BigDecimal("60"), new BigDecimal("50"));
        proposal.addItem(BriefFixture.requirement(), new BigDecimal("40"), new BigDecimal("50"));
        proposal.addExtra("URGENCY", new BigDecimal("0.25"), new BigDecimal("1250"));
        Schedule schedule = proposal.attachSchedule(new BigDecimal("40"));
        Phase design = schedule.addPhase("Design UX", new BigDecimal("0.5000"));
        Phase development = schedule.addPhase("Development", new BigDecimal("0.3000"));
        Phase qa = schedule.addPhase("QA + launch", new BigDecimal("0.2000"));
        schedule.addDependency(development, design);
        schedule.addDependency(qa, development);
        proposal.setDraftedContent("{\"summary\":\"draft\"}");
        proposal.approve(Instant.parse("2026-11-05T10:00:00Z"));
        return proposal;
    }
}
