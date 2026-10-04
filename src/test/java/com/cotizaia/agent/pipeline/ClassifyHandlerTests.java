package com.cotizaia.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.agent.llm.LlmClient;
import com.cotizaia.agent.llm.LlmRequest;
import com.cotizaia.agent.llm.LlmResponse;
import com.cotizaia.agent.llm.PromptBuilder;
import com.cotizaia.agent.llm.StubLlmClient;
import com.cotizaia.domain.Agency;
import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.domain.Client;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Checks classification decisions and malformed model output without Spring. */
class ClassifyHandlerTests {

    @Test
    void unknownTypeFlagsBlockingQuestion() {
        PipelineContext context = context();
        HandlerResult result = handler("{\"projectType\":\"UNKNOWN\",\"confidence\":0.2}").process(context);
        assertThat(result.decision()).isEqualTo(HandlerDecision.FLAG_FOR_HUMAN);
        assertThat(context.getFindings()).extracting(AmbiguityFinding::code).containsExactly("PROJECT_TYPE_UNKNOWN");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "", "null", "{}", "{\"projectType\":\"OTHER\",\"confidence\":0.9}",
            "{\"projectType\":\"WEB_SITE\",\"confidence\":2}",
            "{\"projectType\":\"WEB_SITE\",\"confidence\":0.9} trailing"})
    void invalidResponseFails(String response) {
        assertThatThrownBy(() -> handler(response).process(context())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validTypeProceedsAndStoresConfidence() {
        PipelineContext context = context();
        HandlerResult result = handler("{\"projectType\":\"WEB_SITE\",\"confidence\":0.9}").process(context);
        assertThat(result.decision()).isEqualTo(HandlerDecision.CONTINUE);
        assertThat(context.getProjectType()).isEqualTo("WEB_SITE");
        assertThat(context.getClassificationConfidence()).isEqualByComparingTo("0.9");
        assertThat(context.getFindings()).isEmpty();
    }

    @Test
    void reclassifiesUsingClarificationText() {
        PipelineContext original = context();
        PipelineContext clarified = new PipelineContext(original.getBrief(), 1L, "Agency", "Client",
                List.of(new Clarification("PROJECT_TYPE_UNKNOWN", "Tipo de proyecto?", "Una tienda en linea")));
        ClassifyHandler handler = new ClassifyHandler(new StubLlmClient(), new PromptBuilder(), new ObjectMapper());
        assertThat(handler.process(clarified).decision()).isEqualTo(HandlerDecision.CONTINUE);
        assertThat(clarified.analysisText()).contains("\nAclaracion (Tipo de proyecto?): Una tienda en linea");
        assertThat(clarified.getProjectType()).isEqualTo("ECOMMERCE");
        assertThat(clarified.getFindings()).isEmpty();
    }

    private ClassifyHandler handler(String response) {
        return new ClassifyHandler(client(response), new PromptBuilder(), new ObjectMapper());
    }

    private LlmClient client(String response) {
        return new LlmClient() {
            @Override
            public LlmResponse complete(LlmRequest request) {
                return new LlmResponse(response, "fake", "fake", 0);
            }

            @Override
            public String provider() {
                return "fake";
            }
        };
    }

    private PipelineContext context() {
        Agency agency = new Agency("Agency");
        Client client = new Client(agency, "Client", "client@example.com");
        return new PipelineContext(new Brief(client, BriefChannel.WEB_FORM, "Help", "{}", Instant.now()),
                1L, "Agency", "Client", List.of());
    }
}
