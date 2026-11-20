package com.cotizaia.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.domain.BriefChannel;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WebFormBriefAdapterTests {

    private final WebFormBriefAdapter adapter = new WebFormBriefAdapter();

    @Test
    void theProjectTypeIsPutInFrontOfTheDescription() {
        NormalizedBrief brief = adapter.normalize(Map.of(
                "projectType", "  Tienda   en línea ",
                "description", "Catálogo con carrito"));

        assertThat(brief.rawText()).isEqualTo("Tienda en línea: Catálogo con carrito");
    }

    @Test
    void aMissingOrBlankProjectTypeLeavesTheDescriptionAlone() {
        Map<String, Object> blankType = new HashMap<>();
        blankType.put("projectType", "   ");
        blankType.put("description", "Landing page");
        Map<String, Object> nullType = new HashMap<>();
        nullType.put("projectType", null);
        nullType.put("description", "Landing page");

        assertThat(adapter.normalize(blankType).rawText()).isEqualTo("Landing page");
        assertThat(adapter.normalize(nullType).rawText()).isEqualTo("Landing page");
    }

    @Test
    void theDescriptionIsRequired() {
        assertThatThrownBy(() -> adapter.normalize(Map.of("projectType", "Web")))
                .hasMessageContaining("description");
    }

    @Test
    void formsHaveNoReceivedTimestamp() {
        assertThat(adapter.normalize(Map.of("description", "x")).receivedAt()).isNull();
        assertThat(adapter.channel()).isEqualTo(BriefChannel.WEB_FORM);
    }
}
