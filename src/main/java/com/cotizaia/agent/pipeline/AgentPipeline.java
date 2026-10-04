package com.cotizaia.agent.pipeline;

import com.cotizaia.agent.AgentRun;
import com.cotizaia.agent.PipelineRunRecorder;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Runs the seven-stage agent (project.txt section 6) through the existing durable recorder.
 *
 * <p>Design pattern - <b>Chain of Responsibility</b>: linked handlers own successor forwarding;
 * this component fixes their order once and exposes a single entry point.
 */
@Component
public class AgentPipeline {

    private final PipelineHandler head;

    private final PipelineRunRecorder recorder;

    public AgentPipeline(ClassifyHandler classify, ExtractHandler extract, EstimateHandler estimate,
            PriceHandler price, AmbiguityCheckHandler ambiguity, GenerateHandler generate,
            ComposeHandler compose, PipelineRunRecorder recorder) {
        this.head = classify;
        this.recorder = recorder;
        classify.linkWith(extract).linkWith(estimate).linkWith(price).linkWith(ambiguity)
                .linkWith(generate).linkWith(compose);
    }

    public AgentRun run(PipelineContext context) {
        return recorder.runChain(context.getBrief(), head, context);
    }

    public List<String> handlerNames() {
        return List.of("Classify", "Extract", "Estimate", "Price", "AmbiguityCheck", "Generate", "Compose");
    }
}
