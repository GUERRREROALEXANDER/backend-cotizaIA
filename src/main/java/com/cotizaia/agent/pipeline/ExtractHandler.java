package com.cotizaia.agent.pipeline;

import com.cotizaia.agent.llm.CatalogPromptEntry;
import com.cotizaia.agent.llm.LlmClient;
import com.cotizaia.agent.llm.PromptBuilder;
import com.cotizaia.domain.RequirementType;
import com.cotizaia.repository.RequirementTypeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Maps model output to agency-owned catalog entries (project.txt section 6) so AI cannot invent billable services.
 * Draft requirements remain in memory until a facade can persist the complete business result.
 *
 * <p>Design pattern - <b>Chain of Responsibility</b>: this ConcreteHandler extracts scope before
 * {@link PipelineHandler} forwards its result to deterministic estimation.
 */
@Component
public class ExtractHandler extends PipelineHandler {

    private final LlmClient client;

    private final PromptBuilder prompts;

    private final ObjectMapper mapper;

    private final RequirementTypeRepository repository;

    public ExtractHandler(LlmClient client, PromptBuilder prompts, ObjectMapper mapper,
            RequirementTypeRepository repository) {
        this.client = client;
        this.prompts = prompts;
        this.mapper = mapper;
        this.repository = repository;
    }

    @Override
    public String name() {
        return "Extract";
    }

    @Override
    protected HandlerResult process(PipelineContext context) {
        List<RequirementType> catalog = repository.findByServiceCatalogAgencyIdOrderByIdAsc(context.getAgencyId());
        if (catalog.isEmpty()) {
            throw new IllegalStateException("Agency has no service catalog configured");
        }
        List<CatalogPromptEntry> entries = catalog.stream().map(type -> new CatalogPromptEntry(type.getName(),
                type.getName() + ", " + (type.getDescription() == null ? "" : type.getDescription()))).toList();
        String response = client.complete(prompts.extract(context.analysisText(), entries)).content();
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("Invalid extraction JSON");
        }
        JsonNode root;
        try {
            root = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid extraction JSON", exception);
        }
        if (!root.path("requirements").isArray()) {
            throw new IllegalStateException("Invalid extraction response");
        }
        List<RequirementDraft> drafts = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        boolean unknown = false;
        for (JsonNode item : root.path("requirements")) {
            String requested = normalize(item.path("requirementType").asText());
            RequirementType match = catalog.stream().filter(type -> normalize(type.getName()).equals(requested))
                    .findFirst().orElse(null);
            if (match == null) {
                unknown = true;
                continue;
            }
            if (!seen.add(requested)) {
                continue;
            }
            if (!item.path("confidence").isNumber() || !item.path("description").isTextual()
                    || item.path("description").asText().isBlank()) {
                throw new IllegalStateException("Invalid extracted requirement");
            }
            BigDecimal confidence = item.path("confidence").decimalValue();
            if (confidence.signum() < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalStateException("Invalid extraction confidence");
            }
            drafts.add(new RequirementDraft(match, item.path("description").asText(), confidence));
        }
        context.setRequirements(drafts);
        if (drafts.isEmpty()) {
            context.getFindings().add(new AmbiguityFinding("NO_SCOPE",
                    "\u00bfQue funcionalidades necesitas exactamente? Describe el alcance del proyecto.", true));
            return HandlerResult.flag("no scope extracted");
        }
        return unknown ? HandlerResult.flag("unknown catalog requirements ignored")
                : HandlerResult.proceed(drafts.size() + " requirements extracted");
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).trim();
    }
}
