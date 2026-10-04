package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Domain acceptance for issue #11: a contract is generated as an unissued draft
 * (acceptance 1) and only the issue step, carrying the simulated payment,
 * flips it to ISSUED (acceptance 2). Pure domain tests (no Spring), so the
 * State rule is verified directly.
 */
class ContractClauseDomainTests {

    @Test
    void draftIsCreatedNotIssuedAndCarriesNoPayment() {
        Contract contract = Contract.draftFor(proposal());

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.DRAFT);
        assertThat(contract.getIssuedAt()).isNull();
        assertThat(contract.getPaymentReference()).isNull();
        assertThat(contract.getPaidAmount()).isNull();
        assertThat(contract.getPaidAt()).isNull();
        assertThat(contract.getClauses()).isEmpty();
    }

    @Test
    void clausesGetSequentialOrderAndAreReadOnly() {
        Contract contract = Contract.draftFor(proposal());
        Clause first = contract.addClause("Alcance del proyecto");
        Clause second = contract.addClause("Forma de pago");

        assertThat(first.getOrder()).isEqualTo(1);
        assertThat(second.getOrder()).isEqualTo(2);
        assertThat(contract.getClauses()).extracting(Clause::getText)
                .containsExactly("Alcance del proyecto", "Forma de pago");
        assertThatThrownBy(() -> contract.getClauses().add(first))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void issueRecordsTheSimulatedPaymentAndFlipsToIssued() {
        Contract contract = Contract.draftFor(proposal());

        Instant paidAt = Instant.parse("2026-02-01T10:00:00Z");
        contract.issue("SIM-PAY-001", new BigDecimal("500.00"), paidAt);

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.ISSUED);
        assertThat(contract.getIssuedAt()).isNotNull();
        assertThat(contract.getPaymentReference()).isEqualTo("SIM-PAY-001");
        assertThat(contract.getPaidAmount()).isEqualByComparingTo("500.00");
        assertThat(contract.getPaidAt()).isEqualTo(paidAt);
    }

    @Test
    void rejectsIssuingWithoutPaymentEvidenceAndLeavesTheDraftUntouched() {
        Contract contract = Contract.draftFor(proposal());

        assertThatThrownBy(() -> contract.issue("   ", new BigDecimal("500.00"), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> contract.issue("SIM-1", null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> contract.issue("SIM-1", new BigDecimal("500.00"), null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.DRAFT);
        assertThat(contract.getIssuedAt()).isNull();
        assertThat(contract.getPaymentReference()).isNull();
    }

    @Test
    void rejectsIssuingAnAlreadyIssuedContract() {
        Contract contract = Contract.draftFor(proposal());
        contract.issue("SIM-1", new BigDecimal("500.00"), Instant.now());

        assertThatThrownBy(() -> contract.issue("SIM-2", new BigDecimal("500.00"), Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Illegal contract transition");
        assertThat(contract.getPaymentReference()).isEqualTo("SIM-1");
    }

    @Test
    void returnsNoPublicConstructorSoOnlyTheFactoryCanCreateADraft() {
        assertThat(Contract.class.getConstructors()).isEmpty();
        assertThat(Clause.class.getConstructors()).isEmpty();
    }

    private static Proposal proposal() {
        Proposal proposal = new Proposal.Builder().brief(BriefFixture.brief()).build();
        proposal.addItem(BriefFixture.requirement(), new BigDecimal("10"), new BigDecimal("50"));
        return proposal;
    }
}
