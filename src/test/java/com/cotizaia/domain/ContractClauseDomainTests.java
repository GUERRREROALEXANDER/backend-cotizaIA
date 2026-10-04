package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Domain acceptance for issue #11: a contract is generated as an unissued draft
 * (acceptance 1) and only the issue step flips it to ISSUED (acceptance 2).
 * Pure domain tests (no Spring), so the State rule is verified directly.
 * Payment belongs to a later issue and plays no role here.
 */
class ContractClauseDomainTests {

    @Test
    void draftIsCreatedNotIssued() {
        Contract contract = Contract.draftFor(proposal());

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.DRAFT);
        assertThat(contract.getIssuedAt()).isNull();
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
    void issueFlipsDraftToIssued() {
        Contract contract = Contract.draftFor(proposal());

        contract.issue();

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.ISSUED);
        assertThat(contract.getIssuedAt()).isNotNull();
    }

    @Test
    void rejectsIssuingAnAlreadyIssuedContract() {
        Contract contract = Contract.draftFor(proposal());
        contract.issue();

        assertThatThrownBy(contract::issue)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Illegal contract transition");
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.ISSUED);
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
