package com.cotizaia.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotizaia.agent.llm.LlmClient;
import com.cotizaia.agent.llm.LlmRequest;
import com.cotizaia.agent.llm.LlmResponse;
import com.cotizaia.agent.llm.PromptBuilder;
import com.cotizaia.domain.Agency;
import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.domain.Client;
import com.cotizaia.domain.ServiceCatalog;
import com.cotizaia.repository.RequirementTypeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Checks catalog mapping and malformed extraction output in isolation. */
class ExtractHandlerTests {

    @Test
    void mapsCatalogTypeAndDeduplicates() {
        PipelineContext context = context();
        String item = "{\"requirementType\":\"MEN\u00da DIGITAL\",\"description\":\"Menu\",\"confidence\":0.9}";
        HandlerResult result = handler(context, "{\"requirements\":[" + item + "," + item + "]}")
                .process(context);
        assertThat(result.decision()).isEqualTo(HandlerDecision.CONTINUE);
        assertThat(context.getRequirements()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "", "null", "{}", "{\"requirements\":{}}", "{\"requirements\":[]} trailing"})
    void invalidJsonOrSchemaFails(String response) {
        PipelineContext context = context();
        assertThatThrownBy(() -> handler(context, response).process(context))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingScopeAddsBlockingFinding() {
        PipelineContext context = context();
        HandlerResult result = handler(context, "{\"requirements\":[]}").process(context);
        assertThat(result.decision()).isEqualTo(HandlerDecision.FLAG_FOR_HUMAN);
        assertThat(context.getRequirements()).isEmpty();
        assertThat(context.getFindings()).containsExactly(new AmbiguityFinding("NO_SCOPE",
                "\u00bfQue funcionalidades necesitas exactamente? Describe el alcance del proyecto.", true));
    }

    @Test
    void ignoresUnknownCatalogNamesAndFlagsReview() {
        PipelineContext context = context();
        String response = "{\"requirements\":[{\"requirementType\":\"Invented service\"},"
                + "{\"requirementType\":\"Menu digital\",\"description\":\"Menu\",\"confidence\":0.9}]}";
        HandlerResult result = handler(context, response).process(context);
        assertThat(result.decision()).isEqualTo(HandlerDecision.FLAG_FOR_HUMAN);
        assertThat(context.getRequirements()).singleElement()
                .satisfies(draft -> assertThat(draft.getRequirementType().getName()).isEqualTo("Menu digital"));
    }

    @Test
    void rejectsEmptyAgencyCatalogBeforeCallingModel() {
        RequirementTypeRepository repository = mock(RequirementTypeRepository.class);
        when(repository.findByServiceCatalogAgencyIdOrderByIdAsc(1L)).thenReturn(List.of());
        ExtractHandler handler = new ExtractHandler(null, new PromptBuilder(), new ObjectMapper(), repository);
        assertThatThrownBy(() -> handler.process(context()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Agency has no service catalog configured");
    }

    private ExtractHandler handler(PipelineContext context, String response) {
        RequirementTypeRepository repository = mock(RequirementTypeRepository.class);
        ServiceCatalog catalog = new ServiceCatalog(context.getBrief().getClient().getAgency(), "Web");
        when(repository.findByServiceCatalogAgencyIdOrderByIdAsc(1L))
                .thenReturn(List.of(catalog.addRequirementType("Menu digital", "menu", null)));
        LlmClient client = new LlmClient() {
            @Override
            public LlmResponse complete(LlmRequest request) {
                return new LlmResponse(response, "fake", "fake", 0);
            }

            @Override
            public String provider() {
                return "fake";
            }
        };
        return new ExtractHandler(client, new PromptBuilder(), new ObjectMapper(), repository);
    }

    private PipelineContext context() {
        Agency agency = new Agency("Agency");
        Client client = new Client(agency, "Client", "client@example.com");
        return new PipelineContext(new Brief(client, BriefChannel.WEB_FORM, "Menu", "{}", Instant.now()),
                1L, "Agency", "Client", List.of());
    }
}
