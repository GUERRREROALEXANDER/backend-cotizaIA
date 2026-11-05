package com.cotizaia.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotizaia.domain.Agency;
import com.cotizaia.domain.RequirementType;
import com.cotizaia.domain.ServiceCatalog;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** The retrieval query for one requirement is built from the brief sentences that talk about it. */
class EvidenceQueryBuilderTests {

    private final EvidenceQueryBuilder builder = new EvidenceQueryBuilder();

    private final RequirementType shop = type("Tienda en linea", "tienda, ecommerce, productos, carrito");

    private final RequirementType seo = type("SEO inicial", "seo, google");

    @Test
    void keepsOnlyTheSentencesThatMentionTheRequirement() {
        String brief = "Hola, tenemos una ferretería en Pasto. Queremos vender en línea unos 300 productos!\n"
                + "También nos gustaría salir en Google. Gracias.";
        assertThat(builder.query(brief, shop))
                .isEqualTo("Tienda en linea: Queremos vender en línea unos 300 productos!");
        assertThat(builder.query(brief, seo)).isEqualTo("SEO inicial: También nos gustaría salir en Google.");
    }

    @Test
    void matchesIgnoringAccentsAndCaseAndShortKeywordsAsWholeWords() {
        RequirementType app = type("App movil", "app, android");
        String brief = "Necesitamos una APP. La aplicación de mapas no importa. Approach libre.";
        assertThat(builder.query(brief, app)).isEqualTo("App movil: Necesitamos una APP.");
        assertThat(builder.query("Quiero una TIENDA con carrito", shop))
                .isEqualTo("Tienda en linea: Quiero una TIENDA con carrito");
    }

    @Test
    void fallsBackToTheWholeBriefCappedWhenNothingMatches() {
        String brief = "x".repeat(1500);
        String query = builder.query(brief, seo);
        assertThat(query).startsWith("SEO inicial: ").hasSize("SEO inicial: ".length() + 1000);
    }

    private static RequirementType type(String name, String keywords) {
        return new ServiceCatalog(new Agency("A"), "Web").addRequirementType(name, keywords, new BigDecimal("10"));
    }
}
