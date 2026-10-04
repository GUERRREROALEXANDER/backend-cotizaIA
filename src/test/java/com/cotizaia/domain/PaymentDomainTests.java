package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Checks deposit rounding and ownership without Spring (project.txt section 4). */
class PaymentDomainTests {

    @Test
    void roundsHalfOfTotalsIncludingOddCents() {
        assertDeposit("1000000.00", "500000.00");
        assertDeposit("1234567.89", "617283.95");
        assertDeposit("0.03", "0.02");
        assertDeposit("0.05", "0.03");
    }

    @Test
    void rejectsUnacceptedProposalAndForeignContract() {
        Proposal proposal = proposal("100.00");
        Contract contract = Contract.draftFor(proposal);
        assertThatThrownBy(() -> Payment.simulatedDeposit(proposal, contract, "SIM-a", Instant.now()))
                .isInstanceOf(IllegalStateException.class);
        proposal.transitionTo(ProposalStatus.ANALYZING);
        proposal.transitionTo(ProposalStatus.QUOTED);
        proposal.transitionTo(ProposalStatus.IN_REVIEW);
        proposal.transitionTo(ProposalStatus.SENT);
        proposal.transitionTo(ProposalStatus.ACCEPTED);
        Contract foreign = Contract.draftFor(proposal("100.00"));
        assertThatThrownBy(() -> Payment.simulatedDeposit(proposal, foreign, "SIM-a", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertDeposit(String total, String expected) {
        Proposal proposal = proposal(total);
        proposal.transitionTo(ProposalStatus.ANALYZING);
        proposal.transitionTo(ProposalStatus.QUOTED);
        proposal.transitionTo(ProposalStatus.IN_REVIEW);
        proposal.transitionTo(ProposalStatus.SENT);
        proposal.transitionTo(ProposalStatus.ACCEPTED);
        Payment payment = Payment.simulatedDeposit(proposal, Contract.draftFor(proposal),
                "SIM-test", Instant.now());
        assertThat(payment.getAmount()).isEqualByComparingTo(expected);
        assertThat(payment.getAmount().multiply(new BigDecimal("2")).subtract(proposal.getTotal()).abs())
                .isLessThanOrEqualTo(new BigDecimal("0.01"));
        assertThat(payment.getProvider()).isEqualTo("SIMULATED");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SIMULATED_PAID);
        assertThat(payment.isSimulated()).isTrue();
    }

    private Proposal proposal(String total) {
        return new Proposal.Builder().brief(BriefFixture.brief())
                .addItem(BriefFixture.requirement(), BigDecimal.ONE, new BigDecimal(total)).build();
    }
}
