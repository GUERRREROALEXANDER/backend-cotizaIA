package com.cotizaia.agent.pipeline;

import com.cotizaia.agent.embedding.EmbeddingException;
import com.cotizaia.agent.knowledge.PassageRetriever;
import com.cotizaia.agent.knowledge.RetrievedChunk;
import com.cotizaia.agent.llm.LlmClient;
import com.cotizaia.agent.llm.PromptBuilder;
import com.cotizaia.document.DraftOrigin;
import com.cotizaia.document.DraftParagraph;
import com.cotizaia.document.DraftSection;
import com.cotizaia.document.ProposalDraft;
import com.cotizaia.document.SourceRef;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Drafts the proposal text from the brief and the agency's own past passages, retries once with the
 * validator's findings, and replaces an unsafe or unparseable answer with a safe template.
 */
@Component
public class ProposalDrafter {

    private static final String ALCANCE = "Alcance";

    private static final String SUPUESTOS = "Supuestos";

    private static final String EXCLUSIONES = "Exclusiones";

    private static final String DEFAULT_ASSUMPTION = "El cliente entrega los contenidos y accesos necesarios.";

    private static final String DEFAULT_EXCLUSION = "Publicidad pagada y manejo de redes sociales.";

    private final LlmClient client;

    private final PromptBuilder prompts;

    private final ObjectMapper mapper;

    private final ProposalDraftValidator validator;

    private final PassageRetriever passages;

    private final RetrievalProperties properties;

    public ProposalDrafter(LlmClient client, PromptBuilder prompts, ObjectMapper mapper,
            ProposalDraftValidator validator, PassageRetriever passages, RetrievalProperties properties) {
        this.client = client;
        this.prompts = prompts;
        this.mapper = mapper;
        this.validator = validator;
        this.passages = passages;
        this.properties = properties;
    }

    public Drafted draft(long agencyId, String briefText, String summary, List<RequirementDraft> requirements) {
        List<String> names = requirements.stream().map(requirement -> requirement.getRequirementType().getName())
                .toList();
        List<RetrievedChunk> retrieved = retrieve(agencyId, briefText);
        Set<Long> allowed = retrieved.stream().map(RetrievedChunk::chunkId).collect(Collectors.toSet());
        List<String> violations = List.of();
        for (int attempt = 1; attempt <= 2; attempt++) {
            String content = client.complete(prompts.draftProposal(briefText, names, retrieved, violations))
                    .content();
            JsonNode json = parse(content);
            violations = json == null ? List.of("model output was not valid JSON")
                    : validator.violations(json, briefText, names, allowed);
            if (json != null && violations.isEmpty()) {
                return new Drafted(toDraft(json, briefText, summary, retrieved), List.of(), attempt);
            }
        }
        return new Drafted(template(summary, names), violations, 2);
    }

    private List<RetrievedChunk> retrieve(long agencyId, String briefText) {
        if (properties.isEnabled()) {
            try {
                List<RetrievedChunk> found = passages.retrieve(agencyId, briefText, null, 6);
                if (found != null) {
                    return found;
                }
            } catch (EmbeddingException exception) {
                // Memory is an enhancement: losing it must never lose the proposal.
            }
        }
        return List.of();
    }

    private JsonNode parse(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(content);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private ProposalDraft toDraft(JsonNode json, String briefText, String summary, List<RetrievedChunk> retrieved) {
        Map<Long, RetrievedChunk> byId = new HashMap<>();
        for (RetrievedChunk chunk : retrieved) {
            byId.put(chunk.chunkId(), chunk);
        }
        List<SourceRef> sources = new ArrayList<>();
        Map<Long, Integer> numbers = new HashMap<>();
        List<DraftSection> sections = List.of(
                section(ALCANCE, json.get("scope"), byId, sources, numbers),
                section(SUPUESTOS, json.get("assumptions"), byId, sources, numbers),
                section(EXCLUSIONES, json.get("exclusions"), byId, sources, numbers));
        return new ProposalDraft(summary, sections, sources, DraftOrigin.AI);
    }

    private DraftSection section(String title, JsonNode entries, Map<Long, RetrievedChunk> byId,
            List<SourceRef> sources, Map<Long, Integer> numbers) {
        List<DraftParagraph> paragraphs = new ArrayList<>();
        if (entries != null) {
            for (JsonNode entry : entries) {
                List<Integer> cited = new ArrayList<>();
                JsonNode citations = entry.get("citations");
                if (citations != null) {
                    for (JsonNode citation : citations) {
                        long chunkId = citation.asLong();
                        RetrievedChunk chunk = byId.get(chunkId);
                        if (chunk == null) {
                            continue;
                        }
                        int number = numbers.computeIfAbsent(chunkId, id -> {
                            sources.add(new SourceRef(sources.size() + 1, chunk.documentName(), chunk.page()));
                            return sources.size();
                        });
                        cited.add(number);
                    }
                }
                paragraphs.add(new DraftParagraph(entry.path("text").asText(""), cited));
            }
        }
        return new DraftSection(title, paragraphs);
    }

    private ProposalDraft template(String summary, List<String> requirementNames) {
        List<DraftParagraph> scope = requirementNames.stream()
                .map(name -> new DraftParagraph(
                        "Implementación de " + name + " según lo solicitado por el cliente.", List.of()))
                .toList();
        List<DraftSection> sections = List.of(
                new DraftSection(ALCANCE, scope),
                new DraftSection(SUPUESTOS, List.of(new DraftParagraph(DEFAULT_ASSUMPTION, List.of()))),
                new DraftSection(EXCLUSIONES, List.of(new DraftParagraph(DEFAULT_EXCLUSION, List.of()))));
        return new ProposalDraft(summary, sections, List.of(), DraftOrigin.TEMPLATE);
    }

    /** One drafting outcome: the usable draft, the last AI attempt's findings, and how many calls it took. */
    public record Drafted(ProposalDraft draft, List<String> violations, int attempts) {
    }
}
