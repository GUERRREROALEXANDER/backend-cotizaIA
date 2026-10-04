package com.cotizaia.agent.pipeline;

import org.springframework.stereotype.Component;

/**
 * Assembles the completed quote (project.txt section 2) so a later facade can persist its business rows atomically.
 * Only the run recorder writes during this pass, including when an earlier handler fails.
 *
 * <p>Design pattern - <b>Chain of Responsibility</b>: this terminal ConcreteHandler snapshots the shared context;
 * {@link PipelineHandler} records successful composition through the same step log as every earlier link.
 */
@Component
public class ComposeHandler extends PipelineHandler {

    @Override
    public String name() {
        return "Compose";
    }

    @Override
    protected HandlerResult process(PipelineContext context) {
        context.setDraft(new QuoteDraft(context.getProjectType(), context.getRequirements(), context.getQuote(),
                context.getFindings(), context.getSummary(), context.getDocuments(), context.getPhasePlan()));
        return HandlerResult.proceed("quote draft composed");
    }
}
