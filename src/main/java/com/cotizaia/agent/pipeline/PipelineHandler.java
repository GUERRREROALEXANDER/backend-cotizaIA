package com.cotizaia.agent.pipeline;

import com.cotizaia.domain.StepStatus;

/**
 * Executes one agent stage (project.txt section 6) and forwards to a linked successor.
 *
 * <p>Design pattern - <b>Chain of Responsibility</b>: each concrete handler decides whether to continue,
 * flag review, or halt while this base handler owns forwarding and step reporting.
 */
public abstract class PipelineHandler {

    private PipelineHandler next;

    public final PipelineHandler linkWith(PipelineHandler next) {
        this.next = next;
        return next;
    }

    public abstract String name();

    protected abstract HandlerResult process(PipelineContext context);

    public final void handle(PipelineContext context, StepLog log) {
        if (context.isHalted()) {
            log.record(name(), "halted", "skipped: halted by " + context.getHaltedBy(), null, StepStatus.SKIPPED);
        } else {
            context.setCurrentHandler(name());
            long start = System.nanoTime();
            HandlerResult result = process(context);
            long duration = (System.nanoTime() - start) / 1_000_000;
            String prefix = switch (result.decision()) {
                case CONTINUE -> "";
                case FLAG_FOR_HUMAN -> "[FLAG] ";
                case HALT -> "[HALT] ";
            };
            log.record(name(), "Brief #" + context.getBrief().getId(), prefix + result.summary(),
                    duration, StepStatus.SUCCEEDED);
            if (result.decision() == HandlerDecision.HALT) {
                context.halt(name(), result.summary());
            }
        }
        if (next != null) {
            next.handle(context, log);
        }
    }
}
