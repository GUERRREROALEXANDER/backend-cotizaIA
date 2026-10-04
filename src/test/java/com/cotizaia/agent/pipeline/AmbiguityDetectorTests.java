package com.cotizaia.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotizaia.domain.Agency;
import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.domain.Client;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Verifies deterministic questions independently of AI and persistence. */
class AmbiguityDetectorTests {

    private final AmbiguityDetector detector = new AmbiguityDetector(new ObjectMapper());

    @Test
    void restaurantHasExactlyTwoNonBlockingQuestions() {
        PipelineContext context = context("Hola, tengo un restaurante y necesito una pagina web donde los clientes "
                + "vean el menu y puedan hacer reservas en linea. ¿Cuanto me sale?", "{}", List.of());
        context.setProjectType("WEB_SITE");
        assertThat(detector.detect(context)).extracting(AmbiguityFinding::code)
                .containsExactly("PAGE_COUNT_MISSING", "DEADLINE_MISSING");
        assertThat(detector.detect(context)).allMatch(finding -> !finding.blocking());
    }

    @Test
    void detectsContradiction() {
        PipelineContext context = context("necesito una landing de una sola pagina con varias paginas",
                "{}", List.of());
        context.setProjectType("WEB_SITE");
        assertThat(detector.detect(context)).extracting(AmbiguityFinding::code).contains("CONTRADICTION");
    }

    @Test
    void resolvedCodesAreNotRepeated() {
        PipelineContext context = context("Necesito una web", "{}",
                List.of(new Clarification("PAGE_COUNT_MISSING", "Paginas?", "Lo confirmaremos despues")));
        context.setProjectType("WEB_SITE");
        assertThat(detector.detect(context)).extracting(AmbiguityFinding::code)
                .doesNotContain("PAGE_COUNT_MISSING");
    }

    @Test
    void flagsUnreadableAttachment() {
        PipelineContext context = context("Necesito una web", "{\"attachments\":[{\"text\":\"###123\"}]}",
                List.of());
        assertThat(detector.detect(context)).extracting(AmbiguityFinding::code)
                .contains("UNREADABLE_ATTACHMENT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"3 paginas en 2 semanas", "2 secciones para el mes siguiente",
            "paginas: 4 antes de diciembre", "tres pantallas con plazo de 2 dias"})
    void explicitPageCountAndDeadlineNeedNoQuestions(String text) {
        PipelineContext context = context(text, "{}", List.of());
        context.setProjectType("WEB_SITE");
        assertThat(detector.detect(context)).isEmpty();
    }

    @Test
    void confidenceThresholdIsStrictAndUsesStableListIndex() {
        PipelineContext context = context("3 paginas en 2 semanas", "{}", List.of());
        RequirementDraft catalogEntry = PipelineTestFixture.requirement(BigDecimal.ONE);
        context.setRequirements(List.of(
                new RequirementDraft(catalogEntry.getRequirementType(), "Unclear feature", new BigDecimal("0.59")),
                new RequirementDraft(catalogEntry.getRequirementType(), "Clear feature", new BigDecimal("0.60"))));
        assertThat(detector.detect(context)).extracting(AmbiguityFinding::code)
                .containsExactly("LOW_CONFIDENCE_0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"attachments\":[{}]}", "{\"attachments\":[{\"text\":\"   \"}]}"})
    void missingAttachmentTextIsFlagged(String payload) {
        PipelineContext context = context("Entrega en 2 semanas", payload, List.of());
        assertThat(detector.detect(context)).extracting(AmbiguityFinding::code)
                .containsExactly("UNREADABLE_ATTACHMENT");
    }

    private PipelineContext context(String text, String payload, List<Clarification> clarifications) {
        Agency agency = new Agency("Agency");
        Client client = new Client(agency, "Client", "client@example.com");
        Brief brief = new Brief(client, BriefChannel.WEB_FORM, text, payload, Instant.now());
        return new PipelineContext(brief, 1L, "Agency", "Client", clarifications);
    }
}
