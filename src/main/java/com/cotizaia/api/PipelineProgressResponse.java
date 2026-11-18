package com.cotizaia.api;

import com.cotizaia.agent.PipelineProgressRegistry;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Live pipeline view for one brief (spec 13). Deliberately narrow: handler names and
 * their outcome only — no client text, summaries, durations or ids reach the poller.
 */
public record PipelineProgressResponse(
        boolean running,
        List<Step> steps) {

    public static PipelineProgressResponse from(PipelineProgressRegistry.Snapshot snapshot) {
        return new PipelineProgressResponse(
                snapshot.running(),
                snapshot.steps().stream().map(Step::from).toList());
    }

    public record Step(String name, @Schema(allowableValues = {"SUCCEEDED", "FAILED"}) String status) {

        static Step from(PipelineProgressRegistry.Step step) {
            return new Step(step.name(), step.status());
        }
    }
}
