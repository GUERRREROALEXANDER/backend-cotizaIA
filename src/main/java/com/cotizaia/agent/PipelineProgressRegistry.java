package com.cotizaia.agent;

import com.cotizaia.domain.StepStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory live view of a running pipeline (spec 13): the durable log lands only
 * when the run's transaction commits, so the UI has nothing to poll until then.
 *
 * <p>Design choice — <b>single instance on purpose</b>: this bean keeps state in a
 * {@link ConcurrentHashMap} owned by one JVM. The deployment is a single Render free
 * instance, so no request can reach a replica that did not see the run. Horizontal
 * scaling would need a shared store, which is out of scope for this spec.
 *
 * <p>Lifecycle: {@link #start} opens an entry, {@link #step} appends each handler as
 * the recorder executes it, and {@link #finish}/{@link #fail} close it (running=false).
 * A closed entry is kept {@link #RETENTION} so a poll a moment after the run ends still
 * sees the whole timeline; it is dropped lazily on the next access, with no scheduler.
 */
@Component
public class PipelineProgressRegistry {

    /** How long a closed run stays readable for late pollers. */
    static final Duration RETENTION = Duration.ofMinutes(10);

    private final Map<Long, Run> runs = new ConcurrentHashMap<>();

    private final Clock clock;

    /** Production constructor: wall clock, the registry itself has no time configuration. */
    public PipelineProgressRegistry() {
        this(Clock.systemUTC());
    }

    /** Test seam: expiry is exercised by injecting a clock that can move. */
    public PipelineProgressRegistry(Clock clock) {
        this.clock = clock;
    }

    /** Opens a new run for the brief, replacing any previous (possibly stuck) entry. */
    public void start(Long briefId) {
        evictExpired();
        runs.put(briefId, new Run());
    }

    /**
     * Records one executed handler. Only SUCCEEDED and FAILED are published: the response
     * contract names exactly those two, and a SKIPPED link did not run, so it carries no
     * progress information for the viewer.
     */
    public void step(Long briefId, String name, StepStatus status) {
        Run run = runs.get(briefId);
        if (run == null || name == null) {
            return;
        }
        if (status == StepStatus.SUCCEEDED || status == StepStatus.FAILED) {
            run.add(new Step(name, status.name()));
        }
    }

    /** Closes the run after a success or a deliberate halt: both end the run for the viewer. */
    public void finish(Long briefId) {
        Run run = runs.get(briefId);
        if (run != null) {
            run.close(clock.instant());
        }
    }

    /**
     * Closes the run after a technical failure, so the failing handler is visible as
     * FAILED even though its durable row only lands when the transaction commits.
     */
    public void fail(Long briefId, String handler) {
        Run run = runs.get(briefId);
        if (run == null) {
            return;
        }
        if (handler != null) {
            run.add(new Step(handler, StepStatus.FAILED.name()));
        }
        run.close(clock.instant());
    }

    /** Read access; also the lazy sweep that drops runs closed more than ten minutes ago. */
    public Snapshot snapshot(Long briefId) {
        evictExpired();
        Run run = briefId == null ? null : runs.get(briefId);
        return run == null ? Snapshot.idle() : run.snapshot();
    }

    private void evictExpired() {
        Instant now = clock.instant();
        runs.entrySet().removeIf(entry -> entry.getValue().expired(now));
    }

    /** One published handler: the name already persisted in agent_steps, never client text. */
    public record Step(String name, String status) {
    }

    /** What the endpoint renders: no ids, summaries or durations. */
    public record Snapshot(boolean running, List<Step> steps) {

        static Snapshot idle() {
            return new Snapshot(false, List.of());
        }
    }

    /** Mutable per-brief state; the recorder writes from the run thread while requests read. */
    private static final class Run {

        private final List<Step> steps = new ArrayList<>();

        private boolean running = true;

        private Instant finishedAt;

        private synchronized void add(Step step) {
            steps.add(step);
        }

        private synchronized void close(Instant at) {
            running = false;
            finishedAt = at;
        }

        private synchronized boolean expired(Instant now) {
            return !running && finishedAt != null
                    && Duration.between(finishedAt, now).compareTo(RETENTION) >= 0;
        }

        private synchronized Snapshot snapshot() {
            return new Snapshot(running, List.copyOf(steps));
        }
    }
}
