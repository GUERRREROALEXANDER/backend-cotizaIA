package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Domain acceptance for issue #10: durations derive from estimated hours and,
 * above all, an hour edit on the proposal shifts the schedule in cascade.
 * Pure domain tests (no Spring), so the cascade rule is verified directly.
 */
class ScheduleCascadeTests {

    @Test
    void derivesPhaseDurationsFromHoursAtAFixedWeeklyCapacity() {
        // 120 h at 40 h/week, 50/25/12.5/12.5 % -> 2 / 1 / 1 / 1 weeks.
        Proposal proposal = proposalQuoting("60", "60");
        Schedule schedule = scheduleWithoutDependencies(proposal);

        assertThat(schedule.getTotalHours()).isEqualByComparingTo("120.00");
        assertThat(schedule.getPhases()).extracting(Phase::getWeeks)
                .containsExactly(2, 1, 1, 1);
        assertThat(schedule.getPhases()).extracting(Phase::getStartWeek)
                .containsExactly(1, 1, 1, 1);
        assertThat(schedule.getPhases()).extracting(Phase::getEndWeek)
                .containsExactly(2, 1, 1, 1);
    }

    @Test
    void hourEditShiftsDownstreamPhasesInCascade() {
        Proposal proposal = proposalQuoting("60", "60");
        Schedule schedule = scheduleWithoutDependencies(proposal);

        // Chain the phases finish-to-start: each starts after the previous ends.
        Phase design = schedule.getPhases().get(0);
        Phase development = schedule.getPhases().get(1);
        Phase integration = schedule.getPhases().get(2);
        Phase qa = schedule.getPhases().get(3);
        schedule.addDependency(development, design);
        schedule.addDependency(integration, development);
        schedule.addDependency(qa, integration);
        assertThat(schedule.getPhases().get(1).getStartWeek()).isEqualTo(3);
        assertThat(schedule.getPhases().get(3).getStartWeek()).isEqualTo(5);

        // The human edits the design line's hours at approval time (60 -> 120):
        // the total rises to 180 h, the design phase grows to 3 weeks, and every
        // phase after it moves right.
        QuotedItem designLine = proposal.getItems().get(0);
        assertThat(designLine.getHours()).isEqualByComparingTo("60.00");
        proposal.editHours(designLine, new BigDecimal("120.00"));

        assertThat(schedule.getTotalHours()).isEqualByComparingTo("180.00");
        assertThat(schedule.getPhases().get(0).getWeeks()).isEqualTo(3);
        assertThat(schedule.getPhases().get(0).getEndWeek()).isEqualTo(3);
        // Development follows the longer design and itself grows with the total.
        assertThat(schedule.getPhases().get(1).getWeeks()).isEqualTo(2);
        assertThat(schedule.getPhases().get(1).getStartWeek()).isEqualTo(4);
        assertThat(schedule.getPhases().get(1).getEndWeek()).isEqualTo(5);
        assertThat(schedule.getPhases().get(3).getStartWeek()).isEqualTo(7);
    }

    @Test
    void editingHoursWithoutACycleStillRecalculatesEveryDuration() {
        Proposal proposal = proposalQuoting("60", "60");
        Schedule schedule = scheduleWithoutDependencies(proposal);

        proposal.editHours(proposal.getItems().get(0), new BigDecimal("30.00"));

        assertThat(schedule.getTotalHours()).isEqualByComparingTo("90.00");
        assertThat(schedule.getPhases().get(0).getWeeks()).isEqualTo(2);
        assertThat(schedule.getPhases().get(1).getWeeks()).isEqualTo(1);
    }

    @Test
    void rejectsDirectAndIndirectCyclicDependencies() {
        Proposal proposal = proposalQuoting("60", "60");
        Schedule schedule = scheduleWithoutDependencies(proposal);
        Phase design = schedule.getPhases().get(0);
        Phase development = schedule.getPhases().get(1);
        Phase integration = schedule.getPhases().get(2);

        schedule.addDependency(development, design);
        schedule.addDependency(integration, development);

        // Self-dependency is rejected outright.
        assertThatThrownBy(() -> schedule.addDependency(design, design))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("itself");
        // Closing the loop design -> integration -> development -> design is
        // rejected before any edge is written.
        assertThatThrownBy(() -> schedule.addDependency(design, integration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cycle");
        assertThat(schedule.getPhases().get(0).getDependencies()).isEmpty();
    }

    @Test
    void returnsNoPublicConstructorSoOnlyTheFactoryCanCreateASchedule() {
        assertThat(Schedule.class.getConstructors()).isEmpty();
    }

    /** A proposal with two quoted lines whose hours sum to the given total. */
    private static Proposal proposalQuoting(String firstLineHours, String secondLineHours) {
        Proposal proposal = new Proposal.Builder().brief(BriefFixture.brief()).build();
        proposal.addItem(BriefFixture.requirement(), new BigDecimal(firstLineHours), new BigDecimal("50"));
        proposal.addItem(BriefFixture.requirement(), new BigDecimal(secondLineHours), new BigDecimal("50"));
        return proposal;
    }

    /**
     * Attaches a 50/25/12.5/12.5 % schedule at 40 h/week through the proposal,
     * so an hour edit cascades into it.
     */
    private static Schedule scheduleWithoutDependencies(Proposal proposal) {
        Schedule schedule = proposal.attachSchedule(new BigDecimal("40"));
        schedule.addPhase("Design UX", new BigDecimal("0.5000"));
        schedule.addPhase("Development", new BigDecimal("0.2500"));
        schedule.addPhase("Payment & booking integration", new BigDecimal("0.1250"));
        schedule.addPhase("QA + launch", new BigDecimal("0.1250"));
        return schedule;
    }
}
