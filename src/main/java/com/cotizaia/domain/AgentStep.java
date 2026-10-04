package com.cotizaia.domain;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One link of the agent's Chain of Responsibility, recorded for the live
 * pipeline view (project.txt section 2, section 6 pattern 10). A step is a
 * composed child of {@link AgentExecution}: it never exists on its own and the
 * execution assigns its {@code stepOrder} so the timeline is ordered.
 *
 * <p>{@code inputSummary}/{@code outputSummary} are deliberately short, bounded
 * descriptions rather than full prompts or payloads — the feed has to stay
 * cheap to poll, while the full artifacts live in their own tables.
 * {@code durationMs} is measured by the recorder, so the panel can show where
 * the run spent its time.
 */
@Entity
@Table(
        name = "agent_steps",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_agent_steps_execution_order",
                columnNames = {"execution_id", "step_order"}))
public class AgentStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false)
    private AgentExecution execution;

    @Column(nullable = false, length = 100)
    private String handler;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(name = "input_summary", length = 1000)
    private String inputSummary;

    @Column(name = "output_summary", length = 1000)
    private String outputSummary;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StepStatus status;

    protected AgentStep() {
    }

    AgentStep(
            AgentExecution execution,
            String handler,
            int stepOrder,
            String inputSummary,
            String outputSummary,
            Long durationMs,
            StepStatus status) {
        if (handler == null || handler.isBlank()) {
            throw new IllegalArgumentException("handler must not be blank");
        }
        if (stepOrder < 0) {
            throw new IllegalArgumentException("stepOrder must not be negative");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (durationMs != null && durationMs < 0) {
            throw new IllegalArgumentException("durationMs must not be negative");
        }
        this.execution = execution;
        this.handler = handler;
        this.stepOrder = stepOrder;
        this.inputSummary = inputSummary;
        this.outputSummary = outputSummary;
        this.durationMs = durationMs;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public AgentExecution getExecution() {
        return execution;
    }

    public String getHandler() {
        return handler;
    }

    public int getStepOrder() {
        return stepOrder;
    }

    public String getInputSummary() {
        return inputSummary;
    }

    public String getOutputSummary() {
        return outputSummary;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public StepStatus getStatus() {
        return status;
    }
}
