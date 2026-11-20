package com.cotizaia.domain.spec;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotizaia.domain.Agency;
import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.domain.Client;
import com.cotizaia.domain.ExtractedRequirement;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.domain.ServiceCatalog;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Specification: small rules that combine with and / or / not, and a report that names the failing rule. */
class ProposalSpecificationTests {

    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

    @Test
    void combinatorsFollowBooleanLogic() {
        Specification<Integer> positive = number -> number > 0;
        Specification<Integer> even = number -> number % 2 == 0;

        assertThat(positive.and(even).isSatisfiedBy(4)).isTrue();
        assertThat(positive.and(even).isSatisfiedBy(3)).isFalse();
        assertThat(positive.or(even).isSatisfiedBy(-2)).isTrue();
        assertThat(positive.or(even).isSatisfiedBy(-3)).isFalse();
        assertThat(positive.not().isSatisfiedBy(-1)).isTrue();
        assertThat(Specification.allOf(java.util.List.of(positive, even)).isSatisfiedBy(6)).isTrue();
    }

    @Test
    void aQuotedProposalInReviewIsReadyForApproval() {
        Proposal proposal = proposal(ProposalStatus.IN_REVIEW, new BigDecimal("10"), null);

        ReadinessReport report = ReadinessReport.evaluate(proposal, NOW);

        assertThat(report.ready()).isTrue();
        assertThat(report.required()).allSatisfy(rule -> assertThat(rule.passed()).isTrue());
        assertThat(report.recommended()).singleElement()
                .satisfies(rule -> assertThat(rule.code()).isEqualTo("HAS_DRAFT"))
                .satisfies(rule -> assertThat(rule.passed()).isFalse());
    }

    @Test
    void namesEveryRuleThatBlocksApproval() {
        Proposal proposal = proposal(ProposalStatus.QUOTED, BigDecimal.ZERO, NOW.minusSeconds(60));

        ReadinessReport report = ReadinessReport.evaluate(proposal, NOW);

        assertThat(report.ready()).isFalse();
        assertThat(report.required()).filteredOn(rule -> !rule.passed())
                .extracting(ReadinessReport.RuleResult::code)
                .containsExactly("IN_REVIEW", "POSITIVE_HOURS", "POSITIVE_TOTAL", "STILL_VALID");
    }

    @Test
    void validityAcceptsNoDateOrAFutureDate() {
        Specification<Proposal> stillValid =
                ProposalSpecifications.hasNoExpiryDate().or(ProposalSpecifications.validAfter(NOW));

        assertThat(stillValid.isSatisfiedBy(proposal(ProposalStatus.IN_REVIEW, BigDecimal.ONE, null))).isTrue();
        assertThat(stillValid.isSatisfiedBy(
                proposal(ProposalStatus.IN_REVIEW, BigDecimal.ONE, NOW.plusSeconds(3600)))).isTrue();
        assertThat(stillValid.isSatisfiedBy(
                proposal(ProposalStatus.IN_REVIEW, BigDecimal.ONE, NOW.minusSeconds(1)))).isFalse();
    }

    @Test
    void aProposalWithoutItemsIsNotReady() {
        Proposal empty = new Proposal.Builder().brief(brief()).status(ProposalStatus.IN_REVIEW).build();

        assertThat(ProposalSpecifications.readyForApproval(NOW).isSatisfiedBy(empty)).isFalse();
        assertThat(ProposalSpecifications.hasQuotedItems().isSatisfiedBy(empty)).isFalse();
    }

    private static Proposal proposal(ProposalStatus status, BigDecimal hours, Instant validUntil) {
        Brief brief = brief();
        ServiceCatalog catalog = new ServiceCatalog(brief.getClient().getAgency(), "Web");
        catalog.addRequirementType("Responsive layout", null, null);
        ExtractedRequirement requirement = new ExtractedRequirement(brief, catalog.getRequirementTypes().get(0),
                "Responsive layout", null, new BigDecimal("0.9000"));
        return new Proposal.Builder().brief(brief).status(status).validUntil(validUntil)
                .addItem(requirement, hours, new BigDecimal("120000")).build();
    }

    private static Brief brief() {
        Agency agency = new Agency("Spec Agency");
        Client client = new Client(agency, "Spec Client", "client@spec.co");
        return new Brief(client, BriefChannel.EMAIL, "Necesito una web", "{}", Instant.parse("2026-11-01T09:00:00Z"));
    }
}
