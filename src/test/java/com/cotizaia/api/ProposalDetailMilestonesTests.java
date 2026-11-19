package com.cotizaia.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.cotizaia.api.ProposalDetailResponse.MilestoneResponse;
import com.cotizaia.api.ProposalDetailResponse.ScheduleResponse;
import com.cotizaia.domain.BriefFixture;
import com.cotizaia.domain.Phase;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.Schedule;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Spec 15: "hitos" of the smart schedule are a PURE derivation in the response
 * mapper - one "Entrega: &lt;phase&gt;" per phase at that phase's endWeek, plus a
 * final "Lanzamiento" at the last (latest) end week. Pure unit tests (no Spring,
 * no database): a hand-built 3-phase fixture fixes the exact weeks.
 */
class ProposalDetailMilestonesTests {

    @Test
    void milestonesAreOnePerPhasePlusLaunchAtTheLastEndWeek() {
        // 120 h at 40 h/week, shares 0.25/0.50/0.25 chained finish-to-start
        // -> phase end weeks 1, 3, 4; the project launches at week 4.
        Schedule schedule = threePhaseSchedule(true);
        assertThat(schedule.getPhases()).extracting(Phase::getEndWeek).containsExactly(1, 3, 4);

        ScheduleResponse response = ScheduleResponse.from(schedule);

        assertThat(response.phases()).hasSize(3);
        assertThat(response.milestones())
                .extracting(MilestoneResponse::name, MilestoneResponse::week)
                .containsExactly(
                        tuple("Entrega: Design", 1),
                        tuple("Entrega: Build", 3),
                        tuple("Entrega: QA", 4),
                        tuple("Lanzamiento", 4));
    }

    @Test
    void launchUsesTheLatestEndWeekEvenWithoutDependencies() {
        // Without dependencies every phase starts at week 1: weeks 1/2/1 give
        // end weeks 1, 2, 1. Launch lands on the last (latest) end week, 2.
        Schedule schedule = threePhaseSchedule(false);
        assertThat(schedule.getPhases()).extracting(Phase::getEndWeek).containsExactly(1, 2, 1);

        ScheduleResponse response = ScheduleResponse.from(schedule);

        assertThat(response.milestones())
                .extracting(MilestoneResponse::name, MilestoneResponse::week)
                .containsExactly(
                        tuple("Entrega: Design", 1),
                        tuple("Entrega: Build", 2),
                        tuple("Entrega: QA", 1),
                        tuple("Lanzamiento", 2));
    }

    @Test
    void derivationIsPureAndANullScheduleHasNoMilestones() {
        assertThat(ScheduleResponse.from(null)).isNull();

        // Calling the mapper twice on the same schedule yields identical, fully
        // derived milestones (nothing is stored on the schedule itself).
        Schedule schedule = threePhaseSchedule(true);
        assertThat(ScheduleResponse.from(schedule).milestones())
                .isEqualTo(ScheduleResponse.from(schedule).milestones());
        assertThat(schedule.getPhases()).hasSize(3);
    }

    /** 120 quoted hours, 40 h/week, three phases; chained into a timeline when {@code chained}. */
    private static Schedule threePhaseSchedule(boolean chained) {
        Proposal proposal = new Proposal.Builder().brief(BriefFixture.brief()).build();
        proposal.addItem(BriefFixture.requirement(), new BigDecimal("60"), new BigDecimal("50"));
        proposal.addItem(BriefFixture.requirement(), new BigDecimal("60"), new BigDecimal("50"));
        Schedule schedule = proposal.attachSchedule(new BigDecimal("40"));
        Phase design = schedule.addPhase("Design", new BigDecimal("0.2500"));
        Phase build = schedule.addPhase("Build", new BigDecimal("0.5000"));
        Phase qa = schedule.addPhase("QA", new BigDecimal("0.2500"));
        if (chained) {
            schedule.addDependency(build, design);
            schedule.addDependency(qa, build);
        }
        return schedule;
    }
}
