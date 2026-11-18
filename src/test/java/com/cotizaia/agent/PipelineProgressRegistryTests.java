package com.cotizaia.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotizaia.domain.StepStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Spec 13 unit contract for the in-memory live view: start, step, finish, fail and the
 * ten-minute expiry of a closed run, all driven by an injected {@link Clock} so the test
 * never sleeps.
 */
class PipelineProgressRegistryTests {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-18T10:00:00Z"));

    private final PipelineProgressRegistry registry = new PipelineProgressRegistry(clock);

    @Test
    void unknownBriefIsIdle() {
        PipelineProgressRegistry.Snapshot snapshot = registry.snapshot(404L);

        assertThat(snapshot.running()).isFalse();
        assertThat(snapshot.steps()).isEmpty();
    }

    @Test
    void startOpensARunningEntryWithNoSteps() {
        registry.start(7L);

        PipelineProgressRegistry.Snapshot snapshot = registry.snapshot(7L);

        assertThat(snapshot.running()).isTrue();
        assertThat(snapshot.steps()).isEqualTo(List.of());
    }

    @Test
    void stepsKeepExecutionOrderAndTheirStatus() {
        registry.start(7L);
        registry.step(7L, "Classify", StepStatus.SUCCEEDED);
        registry.step(7L, "Extract", StepStatus.SUCCEEDED);
        registry.step(7L, "Estimate", StepStatus.FAILED);

        PipelineProgressRegistry.Snapshot snapshot = registry.snapshot(7L);

        assertThat(snapshot.running()).isTrue();
        assertThat(snapshot.steps()).containsExactly(
                new PipelineProgressRegistry.Step("Classify", "SUCCEEDED"),
                new PipelineProgressRegistry.Step("Extract", "SUCCEEDED"),
                new PipelineProgressRegistry.Step("Estimate", "FAILED"));
    }

    /** The published contract names only SUCCEEDED and FAILED; a skipped link produced nothing to show. */
    @Test
    void skippedStepsAreNotPublished() {
        registry.start(7L);
        registry.step(7L, "Classify", StepStatus.SKIPPED);
        registry.step(7L, "Extract", StepStatus.SUCCEEDED);

        assertThat(registry.snapshot(7L).steps())
                .extracting(PipelineProgressRegistry.Step::name)
                .containsExactly("Extract");
    }

    @Test
    void finishKeepsTheStepsAndStopsTheRun() {
        registry.start(7L);
        registry.step(7L, "Classify", StepStatus.SUCCEEDED);
        registry.finish(7L);

        PipelineProgressRegistry.Snapshot snapshot = registry.snapshot(7L);

        assertThat(snapshot.running()).isFalse();
        assertThat(snapshot.steps()).extracting(PipelineProgressRegistry.Step::name).containsExactly("Classify");
    }

    @Test
    void failPublishesTheFailingHandlerAsFailedAndStopsTheRun() {
        registry.start(7L);
        registry.step(7L, "Classify", StepStatus.SUCCEEDED);
        registry.fail(7L, "Price");

        PipelineProgressRegistry.Snapshot snapshot = registry.snapshot(7L);

        assertThat(snapshot.running()).isFalse();
        assertThat(snapshot.steps()).containsExactly(
                new PipelineProgressRegistry.Step("Classify", "SUCCEEDED"),
                new PipelineProgressRegistry.Step("Price", "FAILED"));
    }

    @Test
    void stepsForABriefWithoutARunAreIgnored() {
        registry.step(7L, "Classify", StepStatus.SUCCEEDED);
        registry.finish(7L);
        registry.fail(7L, "Price");

        assertThat(registry.snapshot(7L).steps()).isEmpty();
        assertThat(registry.snapshot(7L).running()).isFalse();
    }

    @Test
    void closedRunIsKeptForNineMinutesAndDroppedAtTen() {
        registry.start(7L);
        registry.step(7L, "Classify", StepStatus.SUCCEEDED);
        registry.finish(7L);

        clock.advance(Duration.ofMinutes(9));
        assertThat(registry.snapshot(7L).running()).isFalse();
        assertThat(registry.snapshot(7L).steps()).hasSize(1);

        clock.advance(Duration.ofMinutes(1));
        PipelineProgressRegistry.Snapshot expired = registry.snapshot(7L);
        assertThat(expired.running()).isFalse();
        assertThat(expired.steps()).isEmpty();
    }

    /** Only closed runs expire: a long live run must not vanish from the panel. */
    @Test
    void runningEntrySurvivesPastTheRetentionWindow() {
        registry.start(7L);
        registry.step(7L, "Classify", StepStatus.SUCCEEDED);

        clock.advance(Duration.ofMinutes(30));

        PipelineProgressRegistry.Snapshot snapshot = registry.snapshot(7L);
        assertThat(snapshot.running()).isTrue();
        assertThat(snapshot.steps()).extracting(PipelineProgressRegistry.Step::name).containsExactly("Classify");
    }

    @Test
    void startReplacesThePreviousEntryOfThatBrief() {
        registry.start(7L);
        registry.step(7L, "Classify", StepStatus.SUCCEEDED);
        registry.finish(7L);

        registry.start(7L);
        registry.step(7L, "Classify", StepStatus.SUCCEEDED);

        PipelineProgressRegistry.Snapshot snapshot = registry.snapshot(7L);
        assertThat(snapshot.running()).isTrue();
        assertThat(snapshot.steps()).hasSize(1);
    }

    /** Movable clock: expiry is a function of elapsed time, never of a sleep. */
    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
