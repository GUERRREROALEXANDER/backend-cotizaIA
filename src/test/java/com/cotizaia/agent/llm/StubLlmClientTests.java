package com.cotizaia.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class StubLlmClientTests {

    private final PromptBuilder prompts = new PromptBuilder();
    private final StubLlmClient client = new StubLlmClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void classifiesRestaurantWebsiteAndExtractsMatchingRequirements() throws Exception {
        String brief = "Hola, tengo un restaurante y necesito una pagina web donde los clientes vean el menu "
                + "y puedan hacer reservas en linea";
        JsonNode classification = mapper.readTree(
                client.complete(prompts.classify(brief, List.of("WEB_SITE"))).content());
        assertThat(classification.path("projectType").asText()).isEqualTo("WEB_SITE");

        List<CatalogPromptEntry> catalog = List.of(
                new CatalogPromptEntry("MENU", "menu, carta"),
                new CatalogPromptEntry("BOOKING", "reserva, cita"),
                new CatalogPromptEntry("PAYMENT", "pago"));
        JsonNode extracted = mapper.readTree(client.complete(prompts.extract(brief, catalog)).content());
        assertThat(extracted.path("requirements")).hasSize(2);
        assertThat(extracted.path("requirements").toString()).contains("MENU", "BOOKING").doesNotContain("PAYMENT");
    }

    @Test
    void vagueBriefHasNoClassificationOrRequirements() throws Exception {
        String brief = "hola necesito ayuda";
        JsonNode classification = mapper.readTree(client.complete(prompts.classify(brief, List.of())).content());
        JsonNode extracted = mapper.readTree(client.complete(prompts.extract(brief, List.of())).content());
        assertThat(classification.path("projectType").asText()).isEqualTo("UNKNOWN");
        assertThat(extracted.path("requirements")).isEmpty();
    }
}
