package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Domain acceptance for issue #8: the Proposal can only be created through its
 * Builder, required fields are enforced at build(), and the totals are always
 * re-derived from the quoted items.
 */
class ProposalBuilderTests {

    @Test
    void exposesNoPublicConstructor() {
        assertThat(Proposal.class.getConstructors())
                .as("only the Builder may create a Proposal")
                .isEmpty();
    }

    @Test
    void buildWithoutBriefIsRejected() {
        assertThatThrownBy(() -> new Proposal.Builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("brief");
    }

    @Test
    void minimalBuildDefaultsStatusAndEmptyTotals() {
        Proposal proposal = new Proposal.Builder().brief(brief()).build();

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.RECEIVED);
        assertThat(proposal.getItems()).isEmpty();
        assertThat(proposal.getSubtotal()).isEqualByComparingTo("0.00");
        assertThat(proposal.getTotal()).isEqualByComparingTo("0.00");
        assertThat(proposal.getValidUntil()).isNull();
        assertThat(proposal.getTerms()).isNull();
        assertThat(proposal.getSchedule()).isNull();
        assertThat(proposal.getExtras()).isNull();
    }

    @Test
    void carriesEveryOptionalField() {
        Instant validUntil = Instant.parse("2026-03-01T00:00:00Z");
        Proposal proposal = new Proposal.Builder()
                .brief(brief())
                .status(ProposalStatus.QUOTED)
                .validUntil(validUntil)
                .terms("50% deposit, 50% on delivery")
                .schedule("Week 1-2 design, week 3-6 build")
                .extras("urgency +25%")
                .build();

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.QUOTED);
        assertThat(proposal.getValidUntil()).isEqualTo(validUntil);
        assertThat(proposal.getTerms()).isEqualTo("50% deposit, 50% on delivery");
        assertThat(proposal.getSchedule()).isEqualTo("Week 1-2 design, week 3-6 build");
        assertThat(proposal.getExtras()).isEqualTo("urgency +25%");
    }

    @Test
    void computesLineTotalsAndAggregateTotalFromItems() {
        ExtractedRequirement requirement = requirement();

        Proposal proposal = new Proposal.Builder()
                .brief(requirement.getBrief())
                .addItem(requirement, new BigDecimal("10"), new BigDecimal("50"))
                .addItem(requirement, new BigDecimal("2.5"), new BigDecimal("100"))
                .build();

        assertThat(proposal.getItems()).hasSize(2);
        assertThat(proposal.getItems().get(0).getLineTotal()).isEqualByComparingTo("500.00");
        assertThat(proposal.getItems().get(1).getLineTotal()).isEqualByComparingTo("250.00");
        assertThat(proposal.getSubtotal()).isEqualByComparingTo("750.00");
        assertThat(proposal.getTotal()).isEqualByComparingTo("750.00");
    }

    @Test
    void recomputesTotalsWhenALineIsAddedOrRemovedAfterBuild() {
        ExtractedRequirement requirement = requirement();
        Proposal proposal = new Proposal.Builder()
                .brief(requirement.getBrief())
                .addItem(requirement, BigDecimal.ONE, new BigDecimal("500"))
                .build();

        QuotedItem extra = proposal.addItem(requirement, new BigDecimal("2"), new BigDecimal("25"));
        assertThat(proposal.getSubtotal()).isEqualByComparingTo("550.00");
        assertThat(proposal.getTotal()).isEqualByComparingTo("550.00");

        proposal.removeItem(extra);
        assertThat(proposal.getSubtotal()).isEqualByComparingTo("500.00");
        assertThat(proposal.getTotal()).isEqualByComparingTo("500.00");
    }

    @Test
    void rejectsNegativeOrMissingItemInputs() {
        ExtractedRequirement requirement = requirement();

        assertThatThrownBy(() -> new Proposal.Builder()
                        .brief(requirement.getBrief())
                        .addItem(requirement, new BigDecimal("-1"), BigDecimal.ONE)
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Proposal.Builder()
                        .brief(requirement.getBrief())
                        .addItem(requirement, BigDecimal.ONE, new BigDecimal("-0.01"))
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Proposal.Builder()
                        .brief(requirement.getBrief())
                        .addItem(null, BigDecimal.ONE, BigDecimal.ONE)
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Proposal.Builder()
                        .brief(requirement.getBrief())
                        .addItem(requirement, null, BigDecimal.ONE)
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsLegalLifecycleTransitionAndRejectsIllegalJump() {
        Proposal proposal = new Proposal.Builder().brief(brief()).build();

        proposal.transitionTo(ProposalStatus.ANALYZING);
        proposal.transitionTo(ProposalStatus.QUOTED);
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.QUOTED);

        assertThatThrownBy(() -> proposal.transitionTo(ProposalStatus.CONTRACT_ISSUED))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> proposal.transitionTo(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Brief brief() {
        Agency agency = new Agency("Builder Agency");
        Client client = new Client(agency, "Builder Client", "client@builder.co");
        return new Brief(
                client, BriefChannel.EMAIL, "Necesito una web", "{}", Instant.parse("2026-02-01T09:00:00Z"));
    }

    private static ExtractedRequirement requirement() {
        Brief brief = brief();
        ServiceCatalog catalog = new ServiceCatalog(brief.getClient().getAgency(), "Web");
        catalog.addRequirementType("Responsive layout", null, null);
        return new ExtractedRequirement(
                brief,
                catalog.getRequirementTypes().get(0),
                "Responsive layout",
                null,
                new BigDecimal("0.9000"));
    }
}
