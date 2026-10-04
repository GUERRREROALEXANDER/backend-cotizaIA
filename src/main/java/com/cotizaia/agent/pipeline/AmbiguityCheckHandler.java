package com.cotizaia.agent.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Merges deterministic and upstream questions (project.txt section 2) before generating a provisional quote.
 * Resolved codes are dropped so a new pass can incorporate answers without asking the same question again.
 *
 * <p>Design pattern - <b>Chain of Responsibility</b>: this ConcreteHandler is the ambiguity gate;
 * {@link PipelineHandler} skips successors after its HALT decision while still recording their timeline entries.
 */
@Component
public class AmbiguityCheckHandler extends PipelineHandler {

    private final AmbiguityDetector detector;

    public AmbiguityCheckHandler(AmbiguityDetector detector) {
        this.detector = detector;
    }

    @Override
    public String name() {
        return "AmbiguityCheck";
    }

    @Override
    protected HandlerResult process(PipelineContext context) {
        Map<String, AmbiguityFinding> findings = new LinkedHashMap<>();
        for (AmbiguityFinding finding : context.getFindings()) {
            if (!context.resolvedCodes().contains(finding.code())) {
                findings.putIfAbsent(finding.code(), finding);
            }
        }
        for (AmbiguityFinding finding : detector.detect(context)) {
            if (!context.resolvedCodes().contains(finding.code())) {
                findings.putIfAbsent(finding.code(), finding);
            }
        }
        context.setFindings(new ArrayList<>(findings.values()));
        if (findings.values().stream().anyMatch(AmbiguityFinding::blocking)) {
            return HandlerResult.halt("blocking clarifications required");
        }
        return findings.isEmpty() ? HandlerResult.proceed("no ambiguity")
                : HandlerResult.flag(findings.size() + " questions for human review");
    }
}
