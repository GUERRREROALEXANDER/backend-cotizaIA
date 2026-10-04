package com.cotizaia.agent.pipeline;

import com.cotizaia.domain.Agency;
import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.domain.Client;
import com.cotizaia.domain.RequirementType;
import com.cotizaia.domain.ServiceCatalog;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Builds detached entities so handler tests exercise only their own stage. */
final class PipelineTestFixture {

    private PipelineTestFixture() {
    }

    static PipelineContext context() {
        Agency agency = new Agency("Agency");
        Client client = new Client(agency, "Client", "client@example.com");
        Brief brief = new Brief(client, BriefChannel.WEB_FORM, "Necesito una web", "{}", Instant.now());
        return new PipelineContext(brief, 1L, "Agency", "Client", List.of());
    }

    static RequirementDraft requirement(BigDecimal hours) {
        PipelineContext context = context();
        ServiceCatalog catalog = new ServiceCatalog(context.getBrief().getClient().getAgency(), "Web");
        RequirementType type = catalog.addRequirementType("Diseno responsive", "web", hours);
        return new RequirementDraft(type, "Diseno responsive", new BigDecimal("0.9000"));
    }
}
