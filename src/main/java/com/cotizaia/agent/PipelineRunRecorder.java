package com.cotizaia.agent;

import com.cotizaia.agent.pipeline.PipelineContext;
import com.cotizaia.agent.pipeline.PipelineHandler;
import com.cotizaia.domain.AgentExecution;
import com.cotizaia.domain.Brief;
import com.cotizaia.domain.StepStatus;
import com.cotizaia.repository.AgentExecutionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Records one pass of the agent pipeline as one {@link AgentExecution} plus N
 * step rows (project.txt section 2: live pipeline view; issue #7 acceptance:
 * "Every pipeline run writes one execution + N step rows, even on failure").
 *
 * <p>Design pattern - this is a Template Method around the Chain: {@link #run}
 * fixes the observability skeleton (open a RUNNING execution, invoke each link
 * in order, time it, log its outcome, then close the run as SUCCEEDED/FAILED)
 * while the links themselves supply the variable logic. Because the links are
 * executed inside the template, a link that throws still leaves the execution
 * and the steps already completed in the log — the failure path is identical to
 * the success path except for the terminal status.
 *
 * <p>Transaction choice: the log is committed through a {@link TransactionTemplate}
 * before the failure is rethrown. Writing the FAILED rows and then letting the
 * exception escape the same transaction would mark it rollback-only and erase
 * the very run the demo needs to show, so the transactional block never throws —
 * it returns the failure and {@link #run} rethrows it afterwards.
 */
@Service
public class PipelineRunRecorder {

    private final AgentExecutionRepository agentExecutionRepository;
    private final TransactionTemplate transactionTemplate;

    public PipelineRunRecorder(
            AgentExecutionRepository agentExecutionRepository,
            TransactionTemplate transactionTemplate) {
        this.agentExecutionRepository = agentExecutionRepository;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Runs the chain over {@code brief}, always persisting the run log.
     *
     * @throws PipelineFailedException wrapping the link failure, after the log
     *         has been committed as FAILED.
     */
    public AgentRun run(Brief brief, List<PipelineStep> steps) {
        RunOutcome outcome = transactionTemplate.execute(status -> executeWithinTransaction(brief, steps));
        if (outcome.failure() != null) {
            throw new PipelineFailedException(outcome.executionId(), outcome.failure());
        }
        return new AgentRun(outcome.execution(), outcome.result());
    }

    /**
     * Runs linked handlers through the same commit-before-rethrow skeleton as {@link #run}.
     * A deliberate HALT is successful because it produces clarification questions rather than a technical failure.
     */
    public AgentRun runChain(Brief brief, PipelineHandler head, PipelineContext context) {
        RunOutcome outcome = transactionTemplate.execute(status -> executeChain(brief, head, context));
        if (outcome.failure() != null) {
            throw new PipelineFailedException(outcome.executionId(), outcome.failure());
        }
        return new AgentRun(outcome.execution(), context);
    }

    private RunOutcome executeChain(Brief brief, PipelineHandler head, PipelineContext context) {
        AgentExecution execution = new AgentExecution(brief, Instant.now());
        agentExecutionRepository.saveAndFlush(execution);
        try {
            head.handle(context, (handler, input, output, duration, stepStatus) -> {
                execution.addStep(handler, summarize(input), summarize(output), duration, stepStatus);
                agentExecutionRepository.saveAndFlush(execution);
            });
            execution.succeed(Instant.now());
            agentExecutionRepository.saveAndFlush(execution);
            return new RunOutcome(execution, context, null);
        } catch (RuntimeException failure) {
            String handler = context.getCurrentHandler() == null ? "pipeline" : context.getCurrentHandler();
            execution.addStep(handler, null, summarize(failure.getMessage()), null, StepStatus.FAILED);
            String message = failure.getMessage();
            execution.fail(summarize(message == null || message.isBlank() ? failure.toString() : message),
                    Instant.now());
            agentExecutionRepository.saveAndFlush(execution);
            return new RunOutcome(execution, context, failure);
        }
    }

    private RunOutcome executeWithinTransaction(Brief brief, List<PipelineStep> steps) {
        AgentExecution execution = new AgentExecution(brief, Instant.now());
        agentExecutionRepository.saveAndFlush(execution);

        Object context = brief;
        try {
            for (PipelineStep step : steps) {
                context = recordStep(execution, step, context);
            }
            execution.succeed(Instant.now());
            agentExecutionRepository.saveAndFlush(execution);
            return new RunOutcome(execution, context, null);
        } catch (RuntimeException failure) {
            // The log must survive the failure, so the execution is closed here
            // and the exception is carried out instead of thrown, keeping this
            // transaction commit-safe.
            execution.addStep(
                    failureHandlerName(steps, execution),
                    summarize(context),
                    summarize(failure.getMessage()),
                    null,
                    StepStatus.FAILED);
            execution.fail(failure.getMessage() != null ? failure.getMessage() : failure.toString(), Instant.now());
            agentExecutionRepository.saveAndFlush(execution);
            return new RunOutcome(execution, context, failure);
        }
    }

    private Object recordStep(AgentExecution execution, PipelineStep step, Object context) {
        Instant start = Instant.now();
        Object output = step.execute(context);
        long durationMs = Duration.between(start, Instant.now()).toMillis();
        execution.addStep(
                step.name(),
                summarize(context),
                summarize(output),
                durationMs,
                StepStatus.SUCCEEDED);
        agentExecutionRepository.saveAndFlush(execution);
        return output;
    }

    /** Names the failing link; falls back to a synthetic name if none ran yet. */
    private String failureHandlerName(List<PipelineStep> steps, AgentExecution execution) {
        int reached = execution.getSteps().size();
        return reached < steps.size() ? steps.get(reached).name() : "pipeline";
    }

    private String summarize(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.length() <= 1000 ? text : text.substring(0, 1000);
    }

    /** Carries a run's persisted result (or the caught failure) out of the tx. */
    private record RunOutcome(AgentExecution execution, Object result, RuntimeException failure) {

        Long executionId() {
            return execution.getId();
        }
    }

    /**
     * Wraps a chain failure after its run log has been committed, so callers can
     * distinguish "pipeline failed" from "server error" and still read the log.
     */
    public static class PipelineFailedException extends RuntimeException {

        private final Long executionId;

        public PipelineFailedException(Long executionId, Throwable cause) {
            super("Pipeline run failed; see execution " + executionId, cause);
            this.executionId = executionId;
        }

        public Long getExecutionId() {
            return executionId;
        }
    }
}
