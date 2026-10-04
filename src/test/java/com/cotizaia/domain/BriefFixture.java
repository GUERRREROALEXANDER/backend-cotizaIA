package com.cotizaia.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Shared in-memory fixture for domain tests: builds a brief and one extracted
 * requirement with the same defaults the persistence tests use.
 */
final class BriefFixture {

    private BriefFixture() {
    }

    static Brief brief() {
        Agency agency = new Agency("Schedule Agency");
        Client client = new Client(agency, "Schedule Client", "client@schedule.co");
        return new Brief(
                client, BriefChannel.WEB_FORM, "Necesito una web con reservas", "{}", Instant.now());
    }

    static ExtractedRequirement requirement() {
        Brief brief = brief();
        ServiceCatalog catalog = new ServiceCatalog(brief.getClient().getAgency(), "Web");
        catalog.addRequirementType("Responsive layout", null, null);
        return new ExtractedRequirement(
                brief,
                catalog.getRequirementTypes().get(0),
                "Responsive layout",
                new BigDecimal("20.00"),
                new BigDecimal("0.9000"));
    }
}
