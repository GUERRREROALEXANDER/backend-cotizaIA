package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Acceptance for issue #9 (project.txt section 6 pattern 6 Decorator): the base
 * quote plus urgency and discount stack in any order with the same correct
 * total, and adding a new extra is a new decorator class only.
 */
class ProposalPricingDecoratorTests {

    @Test
    void baseExposesSubtotalAsTotal() {
        PricedProposal base = new BaseProposalPrice(proposal());

        assertThat(base.subtotal()).isEqualByComparingTo("750.00");
        assertThat(base.total()).isEqualByComparingTo("750.00");
    }

    @Test
    void urgencyAddsTwentyFivePercent() {
        PricedProposal priced = new UrgencyDecorator(new BaseProposalPrice(proposal()));

        assertThat(priced.total()).isEqualByComparingTo("937.50");
        assertThat(priced.subtotal()).isEqualByComparingTo("750.00");
    }

    @Test
    void discountTakesTenPercent() {
        PricedProposal priced = new DiscountDecorator(new BaseProposalPrice(proposal()));

        assertThat(priced.total()).isEqualByComparingTo("675.00");
    }

    @Test
    void urgencyAndDiscountComposeInAnyOrderWithSameTotal() {
        PricedProposal base = new BaseProposalPrice(proposal());

        PricedProposal urgencyThenDiscount =
                new DiscountDecorator(new UrgencyDecorator(base));
        PricedProposal discountThenUrgency =
                new UrgencyDecorator(new DiscountDecorator(base));

        assertThat(urgencyThenDiscount.total()).isEqualByComparingTo("843.75");
        assertThat(discountThenUrgency.total())
                .isEqualByComparingTo(urgencyThenDiscount.total());
    }

    @Test
    void warrantyAddsFlatFee() {
        ExtendedWarrantyDecorator priced =
                new ExtendedWarrantyDecorator(new BaseProposalPrice(proposal()));

        assertThat(priced.total()).isEqualByComparingTo("900.00");
        assertThat(priced.value()).isEqualByComparingTo("150.00");
    }

    @Test
    void reportsKindRateAndSignedAmount() {
        PricedProposal base = new BaseProposalPrice(proposal());

        ProposalPriceDecorator urgency = new UrgencyDecorator(base);
        assertThat(urgency.extraType()).isEqualTo("URGENCY");
        assertThat(urgency.value()).isEqualByComparingTo("0.25");
        assertThat(urgency.amount()).isEqualByComparingTo("187.50");

        ProposalPriceDecorator discount = new DiscountDecorator(urgency);
        assertThat(discount.extraType()).isEqualTo("DISCOUNT");
        assertThat(discount.value()).isEqualByComparingTo("0.10");
        assertThat(discount.amount()).isEqualByComparingTo("-93.75");
    }

    @Test
    void stackingKeepsSubtotalUntouched() {
        PricedProposal priced = new ExtendedWarrantyDecorator(
                new DiscountDecorator(new UrgencyDecorator(new BaseProposalPrice(proposal()))));

        assertThat(priced.subtotal()).isEqualByComparingTo("750.00");
    }

    @Test
    void addingANewExtraIsJustANewClass() {
        PricedProposal priced = new MarketingDecorator(
                new DiscountDecorator(new UrgencyDecorator(new BaseProposalPrice(proposal()))));

        // No existing class changed: the new extra plugged into the same chain.
        assertThat(priced.total()).isEqualByComparingTo("885.94");
    }

    @Test
    void recordingTheChainOnTheProposalMatchesTheDecoratedTotal() {
        Proposal proposal = proposal();
        ProposalPriceDecorator priced =
                new DiscountDecorator(new UrgencyDecorator(new BaseProposalPrice(proposal)));

        priced.recordOn(proposal);

        assertThat(proposal.getAppliedExtras()).hasSize(2);
        assertThat(proposal.getAppliedExtras().get(0).getType()).isEqualTo("URGENCY");
        assertThat(proposal.getAppliedExtras().get(1).getType()).isEqualTo("DISCOUNT");
        assertThat(proposal.getSubtotal()).isEqualByComparingTo("750.00");
        assertThat(proposal.getTotal()).isEqualByComparingTo(priced.total());
        assertThat(proposal.getTotal()).isEqualByComparingTo("843.75");
    }

    @Test
    void rejectsNullDelegate() {
        assertThatThrownBy(() -> new UrgencyDecorator(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExtendedWarrantyDecorator(new BaseProposalPrice(BigDecimal.ONE), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** A brand-new extra proves the extension point is a subclass alone. */
    private static final class MarketingDecorator extends ProposalPriceDecorator {

        private static final BigDecimal RATE = new BigDecimal("0.05");

        private MarketingDecorator(PricedProposal delegate) {
            super(delegate);
        }

        @Override
        public BigDecimal total() {
            return money(delegate.total().multiply(BigDecimal.ONE.add(RATE)));
        }

        @Override
        public String extraType() {
            return "MARKETING";
        }

        @Override
        public BigDecimal value() {
            return RATE;
        }
    }

    private static Proposal proposal() {
        ExtractedRequirement requirement = requirement();
        return new Proposal.Builder()
                .brief(requirement.getBrief())
                .addItem(requirement, new BigDecimal("10"), new BigDecimal("50"))
                .addItem(requirement, new BigDecimal("2.5"), new BigDecimal("100"))
                .build();
    }

    private static ExtractedRequirement requirement() {
        Agency agency = new Agency("Decorator Agency");
        Client client = new Client(agency, "Decorator Client", "client@decorator.co");
        Brief brief = new Brief(
                client, BriefChannel.EMAIL, "Necesito una web", "{}", Instant.parse("2026-02-01T09:00:00Z"));
        ServiceCatalog catalog = new ServiceCatalog(agency, "Web");
        catalog.addRequirementType("Responsive layout", null, null);
        return new ExtractedRequirement(
                brief,
                catalog.getRequirementTypes().get(0),
                "Responsive layout",
                null,
                new BigDecimal("0.9000"));
    }
}
