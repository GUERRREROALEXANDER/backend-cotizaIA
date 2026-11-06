package com.cotizaia.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The guardrail between model text and a client-facing PDF: money, hours and invented numbers never pass. */
class ProposalDraftValidatorTests {

    private static final String BRIEF = "Necesito una tienda con 300 productos y salir en Google.";

    private static final List<String> REQUIREMENTS = List.of("Tienda en linea", "SEO inicial");

    private final ProposalDraftValidator validator = new ProposalDraftValidator();

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsAGroundedDraft() throws Exception {
        assertThat(validator.violations(draft("Tienda en línea para 300 productos con carrito.", "[10]"), BRIEF,
                REQUIREMENTS, Set.of(10L, 11L))).isEmpty();
    }

    @Test
    void rejectsMoneyInAnyForm() throws Exception {
        for (String text : List.of("Precio total de la tienda", "Tienda por $ especial", "Tienda en COP",
                "Valor de la tienda", "Tienda por pocos pesos", "El costo de la tienda", "Tarifa preferencial")) {
            assertThat(validator.violations(draft(text, "[]"), BRIEF, REQUIREMENTS, Set.of())).as(text).isNotEmpty();
        }
    }

    @Test
    void rejectsHoursAndNumbersTheClientNeverGave() throws Exception {
        assertThat(validator.violations(draft("Tienda lista en 40 horas.", "[]"), BRIEF, REQUIREMENTS, Set.of()))
                .isNotEmpty();
        assertThat(validator.violations(draft("Tienda con 500 productos.", "[]"), BRIEF, REQUIREMENTS, Set.of()))
                .anyMatch(violation -> violation.contains("500"));
    }

    @Test
    void rejectsCitationsOutsideTheRetrievedPassages() throws Exception {
        assertThat(validator.violations(draft("Tienda con carrito.", "[99]"), BRIEF, REQUIREMENTS, Set.of(10L)))
                .anyMatch(violation -> violation.contains("99"));
    }

    @Test
    void rejectsMissingOrExtraScopeAndBadShape() throws Exception {
        JsonNode missing = mapper.readTree("""
                {"scope":[{"requirement":"Tienda en linea","text":"Tienda.","citations":[]}],
                 "assumptions":[{"text":"Supuesto.","citations":[]}],"exclusions":[{"text":"Nada.","citations":[]}]}""");
        assertThat(validator.violations(missing, BRIEF, REQUIREMENTS, Set.of())).isNotEmpty();

        JsonNode extra = mapper.readTree(draft("Tienda.", "[]").toString()
                .replace("\"scope\":[", "\"scope\":[{\"requirement\":\"App movil\",\"text\":\"App.\",\"citations\":[]},"));
        assertThat(validator.violations(extra, BRIEF, REQUIREMENTS, Set.of())).isNotEmpty();

        JsonNode blank = mapper.readTree("""
                {"scope":[{"requirement":"Tienda en linea","text":" ","citations":[]},
                          {"requirement":"SEO inicial","text":"SEO.","citations":[]}],
                 "assumptions":[],"exclusions":[{"text":"Nada.","citations":[]}]}""");
        assertThat(validator.violations(blank, BRIEF, REQUIREMENTS, Set.of())).hasSizeGreaterThanOrEqualTo(2);

        assertThat(validator.violations(mapper.readTree("{\"scope\":\"x\"}"), BRIEF, REQUIREMENTS, Set.of()))
                .isNotEmpty();
    }

    @Test
    void matchesRequirementNamesIgnoringAccentsAndCase() throws Exception {
        JsonNode accented = mapper.readTree(draft("Tienda.", "[]").toString()
                .replace("\"Tienda en linea\"", "\"TIENDA EN LÍNEA\""));
        assertThat(validator.violations(accented, BRIEF, REQUIREMENTS, Set.of())).isEmpty();
    }

    private JsonNode draft(String shopText, String shopCitations) throws Exception {
        return mapper.readTree("""
                {"scope":[{"requirement":"Tienda en linea","text":"%s","citations":%s},
                          {"requirement":"SEO inicial","text":"Optimización inicial para Google.","citations":[]}],
                 "assumptions":[{"text":"El cliente entrega fotos y textos.","citations":[]}],
                 "exclusions":[{"text":"Publicidad pagada.","citations":[]}]}""".formatted(shopText, shopCitations));
    }
}
