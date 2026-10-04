package com.cotizaia.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Run log for one pass of the agent's Chain of Responsibility over a
 * {@link Brief} (project.txt section 2: "Ver el pipeline del agente ejecutarse
 * en vivo"). It is the aggregate root of the observability feed: it composes
 * its {@link AgentStep} children and owns their lifecycle, so the whole run —
 * one execution plus N steps — is written and read as a single unit.
 *
 * <p>Design pattern - the mutation methods enforce the terminal-state invariant
 * in the domain ({@link #succeed()} / {@link #fail(String)}), mirroring
 * {@code ck_agent_executions_finished}: a run that is no longer {@code RUNNING}
 * cannot be closed twice, and a running run cannot be read as finished. This is
 * what makes "one execution + N step rows, even on failure" a guarantee rather
 * than a convention — the recorder always calls exactly one terminal method.
 */
@Entity
@Table(name = "agent_executions")
public class AgentExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brief_id", nullable = false)
    private Brief brief;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExecutionStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @OneToMany(mappedBy = "execution", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AgentStep> steps = new ArrayList<>();

    protected AgentExecution() {
    }

    public AgentExecution(Brief brief, Instant startedAt) {
        if (brief == null) {
            throw new IllegalArgumentException("brief must not be null");
        }
        this.brief = brief;
        this.startedAt = startedAt != null ? startedAt : Instant.now();
        this.status = ExecutionStatus.RUNNING;
    }

    /**
     * Appends one Chain link's result. The step order is derived from the
     * current size, so the timeline is gapless and the UNIQUE(execution_id,
     * step_order) constraint can never be violated by the caller.
     */
    public AgentStep addStep(
            String handler,
            String inputSummary,
            String outputSummary,
            Long durationMs,
            StepStatus stepStatus) {
        AgentStep step = new AgentStep(
                this, handler, steps.size(), inputSummary, outputSummary, durationMs, stepStatus);
        steps.add(step);
        return step;
    }

    /** Closes a clean pass; the run may only be closed once. */
    public void succeed(Instant finishedAt) {
        close(ExecutionStatus.SUCCEEDED, finishedAt, null);
    }

    /** Closes a failed pass and records why, so the failure is never silent. */
    public void fail(String errorMessage, Instant finishedAt) {
        if (errorMessage == null || errorMessage.isBlank()) {
            throw new IllegalArgumentException("errorMessage must not be blank");
        }
        close(ExecutionStatus.FAILED, finishedAt, errorMessage);
    }

    private void close(ExecutionStatus terminal, Instant finishedAt, String errorMessage) {
        if (status != ExecutionStatus.RUNNING) {
            throw new IllegalStateException("Execution is already finished: " + status);
        }
        this.status = terminal;
        this.finishedAt = finishedAt != null ? finishedAt : Instant.now();
        this.errorMessage = errorMessage;
    }

    public boolean isFinished() {
        return status != ExecutionStatus.RUNNING;
    }

    public Long getId() {
        return id;
    }

    public Brief getBrief() {
        return brief;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    /** Unmodifiable view: the timeline is appended through {@link #addStep}. */
    public List<AgentStep> getSteps() {
        return Collections.unmodifiableList(steps);
    }
}
