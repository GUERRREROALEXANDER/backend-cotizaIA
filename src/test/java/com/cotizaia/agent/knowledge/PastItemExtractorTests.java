package com.cotizaia.agent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotizaia.agent.llm.LlmClient;
import com.cotizaia.agent.llm.LlmRequest;
import com.cotizaia.agent.llm.LlmResponse;
import com.cotizaia.agent.llm.LlmTask;
import com.cotizaia.agent.llm.PromptBuilder;
import com.cotizaia.agent.llm.StubLlmClient;
import com.cotizaia.ingest.PdfBriefReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Line items extracted from a past proposal only count when they are grounded: the requirement type exists in the
 * agency catalog and the hours figure literally appears on that page. Anything else is rejected, never stored.
 */
class PastItemExtractorTests {

    static final List<String> CATALOG = List.of("Sitio web informativo", "Tienda en linea", "Sistema de reservas",
            "Pasarela de pagos", "Blog y gestor de contenidos", "Integracion WhatsApp", "Sitio multilenguaje",
            "Identidad de marca", "Panel administrativo", "SEO inicial");

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void extractsTheSeedProposalTableExactly() throws Exception {
        List<String> pages = new PdfBriefReader().readPages(
                Files.readAllBytes(Path.of("seed/pdf/train/P01-propuesta.pdf")));
        PastItemExtractor extractor = new PastItemExtractor(new StubLlmClient(), new PromptBuilder(), mapper);

        PastItemExtractor.Result result = extractor.extract(pages, CATALOG);

        assertThat(result.items()).extracting(PastItemExtractor.Item::requirementType)
                .containsExactly("Tienda en linea", "Pasarela de pagos", "Sistema de reservas");
        assertThat(result.items()).extracting(PastItemExtractor.Item::hours)
                .containsExactly(new BigDecimal("50"), new BigDecimal("13"), new BigDecimal("57"));
        assertThat(result.items()).allSatisfy(item -> {
            assertThat(item.page()).isEqualTo(1);
            assertThat(item.description()).isNotBlank();
        });
        assertThat(result.items().get(0).description()).contains("150 productos");
        assertThat(result.rejected()).isZero();
    }

    @Test
    void rejectsHoursThatDoNotAppearOnThePage() {
        LlmClient hallucinating = fixed("{\"items\":[{\"requirementType\":\"Tienda en linea\","
                + "\"description\":\"Tienda\",\"hours\":99},{\"requirementType\":\"Tienda en linea\","
                + "\"description\":\"Tienda real\",\"hours\":50}]}");
        PastItemExtractor.Result result = new PastItemExtractor(hallucinating, new PromptBuilder(), mapper)
                .extract(List.of("Tienda en linea\nTienda real\n50 h $4.750.000 COP"), CATALOG);
        assertThat(result.items()).extracting(PastItemExtractor.Item::hours).containsExactly(new BigDecimal("50"));
        assertThat(result.rejected()).isEqualTo(1);
    }

    @Test
    void rejectsTypesOutsideTheCatalogAndAbsurdHours() {
        LlmClient model = fixed("{\"items\":[{\"requirementType\":\"Hosting premium\",\"description\":\"x\",\"hours\":5},"
                + "{\"requirementType\":\"SEO inicial\",\"description\":\"y\",\"hours\":0},"
                + "{\"requirementType\":\"seo INICIAL\",\"description\":\"SEO de 9 paginas\",\"hours\":15}]}");
        PastItemExtractor.Result result = new PastItemExtractor(model, new PromptBuilder(), mapper)
                .extract(List.of("Hosting premium 5 h\nSEO inicial 0 h\nSEO de 9 paginas 15 h"), CATALOG);
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.requirementType()).isEqualTo("SEO inicial");
            assertThat(item.hours()).isEqualByComparingTo("15");
        });
        assertThat(result.rejected()).isEqualTo(2);
    }

    @Test
    void malformedModelOutputRejectsThePageInsteadOfFailingTheUpload() {
        PastItemExtractor.Result result = new PastItemExtractor(fixed("not json"), new PromptBuilder(), mapper)
                .extract(List.of("Tienda en linea 50 h"), CATALOG);
        assertThat(result.items()).isEmpty();
    }

    @Test
    void sendsOnePagePerRequestWithTheExtractItemsTask() {
        RecordingClient model = new RecordingClient();
        new PastItemExtractor(model, new PromptBuilder(), mapper).extract(List.of("pagina uno", "", "pagina tres"),
                CATALOG);
        assertThat(model.requests).hasSize(2).allSatisfy(request -> {
            assertThat(request.task()).isEqualTo(LlmTask.EXTRACT_ITEMS);
            assertThat(request.jsonOutput()).isTrue();
            assertThat(PromptBuilder.section(request.userPrompt(), "catalog")).isPresent();
        });
    }

    private LlmClient fixed(String content) {
        return new LlmClient() {
            @Override
            public LlmResponse complete(LlmRequest request) {
                return new LlmResponse(content, "fixed", "fixed", 0);
            }

            @Override
            public String provider() {
                return "fixed";
            }
        };
    }

    static class RecordingClient implements LlmClient {

        final java.util.List<LlmRequest> requests = new java.util.ArrayList<>();

        @Override
        public LlmResponse complete(LlmRequest request) {
            requests.add(request);
            return new LlmResponse("{\"items\":[]}", "rec", "rec", 0);
        }

        @Override
        public String provider() {
            return "rec";
        }
    }
}
