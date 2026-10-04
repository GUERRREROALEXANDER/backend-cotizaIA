package com.cotizaia.agent.pipeline;

import com.cotizaia.agent.llm.LlmClient;
import com.cotizaia.agent.llm.PromptBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Classifies a brief (project.txt section 6) before catalog extraction can interpret its scope.
 * Unknown types produce a blocking question for the later ambiguity gate rather than an invented project type.
 *
 * <p>Design pattern - <b>Chain of Responsibility</b>: this ConcreteHandler classifies the shared context;
 * {@link PipelineHandler} reports its decision and forwards to the extraction successor.
 */
@Component
public class ClassifyHandler extends PipelineHandler {

    private static final List<String> TYPES = List.of("WEB_SITE", "ECOMMERCE", "MOBILE_APP", "BRANDING",
            "MARKETING", "UNKNOWN");

    private final LlmClient client;

    private final PromptBuilder prompts;

    private final ObjectMapper mapper;

    public ClassifyHandler(LlmClient client, PromptBuilder prompts, ObjectMapper mapper) {
        this.client = client;
        this.prompts = prompts;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "Classify";
    }

    @Override
    protected HandlerResult process(PipelineContext context) {
        String response = client.complete(prompts.classify(context.analysisText(), TYPES)).content();
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("Invalid classification JSON");
        }
        try {
            JsonNode root = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(response);
            String type = root.path("projectType").asText();
            JsonNode confidence = root.path("confidence");
            if (!TYPES.contains(type) || !confidence.isNumber()) {
                throw new IllegalStateException("Invalid classification response");
            }
            BigDecimal value = confidence.decimalValue();
            if (value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalStateException("Invalid classification confidence");
            }
            context.setProjectType(type);
            context.setClassificationConfidence(value);
            if ("UNKNOWN".equals(type) && !context.resolvedCodes().contains("PROJECT_TYPE_UNKNOWN")) {
                context.getFindings().add(new AmbiguityFinding("PROJECT_TYPE_UNKNOWN",
                        "\u00bfQue tipo de proyecto necesitas? "
                                + "(pagina web, tienda en linea, app movil, marca...)", true));
                return HandlerResult.flag("project type unknown");
            }
            return HandlerResult.proceed("project type " + type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid classification JSON", exception);
        }
    }
}
