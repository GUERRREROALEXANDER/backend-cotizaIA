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
 *
 * <p>The successor links are rebuilt before every run because handlers are singleton beans: a hand-built chain
 * (tests, tools) may temporarily re-point a shared handler, and a later run must never inherit that wiring.
 */
@Component
public class AgentPipeline {

    private final ClassifyHandler classify;

    private final ExtractHandler extract;

    private final RetrieveHandler retrieve;

    private final EstimateHandler estimate;

    private final PriceHandler price;

    private final AmbiguityCheckHandler ambiguity;

    private final GenerateHandler generate;

    private final ComposeHandler compose;

    private final PipelineRunRecorder recorder;

    public AgentPipeline(ClassifyHandler classify, ExtractHandler extract, RetrieveHandler retrieve,
            EstimateHandler estimate, PriceHandler price, AmbiguityCheckHandler ambiguity, GenerateHandler generate,
            ComposeHandler compose, PipelineRunRecorder recorder) {
        this.classify = classify;
        this.extract = extract;
        this.retrieve = retrieve;
        this.estimate = estimate;
        this.price = price;
        this.ambiguity = ambiguity;
        this.generate = generate;
        this.compose = compose;
        this.recorder = recorder;
        link();
    }

    public AgentRun run(PipelineContext context) {
        link();
        return recorder.runChain(context.getBrief(), classify, context);
    }

    public List<String> handlerNames() {
        return List.of("Classify", "Extract", "Retrieve", "Estimate", "Price", "AmbiguityCheck", "Generate", "Compose");
    }

    private void link() {
        classify.linkWith(extract).linkWith(retrieve).linkWith(estimate).linkWith(price).linkWith(ambiguity)
                .linkWith(generate).linkWith(compose);
        compose.linkWith(null);
    }
}
