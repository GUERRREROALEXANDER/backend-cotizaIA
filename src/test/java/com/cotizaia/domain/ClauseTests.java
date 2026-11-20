package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class ClauseTests {

    private final Contract contract = mock(Contract.class);

    @Test
    void aClauseBelongsToItsContractInOrder() {
        Clause clause = new Clause(contract, "El cliente entrega los textos y fotos.", 3);

        assertThat(clause.getContract()).isSameAs(contract);
        assertThat(clause.getOrder()).isEqualTo(3);
        assertThat(clause.getText()).isEqualTo("El cliente entrega los textos y fotos.");
    }

    @Test
    void clausesAreNumberedFromOne() {
        assertThatThrownBy(() -> new Clause(contract, "Texto", 0)).hasMessageContaining("order");
        assertThat(new Clause(contract, "Texto", 1).getOrder()).isEqualTo(1);
    }

    @Test
    void contractAndTextAreRequired() {
        assertThatThrownBy(() -> new Clause(null, "Texto", 1)).hasMessageContaining("contract");
        assertThatThrownBy(() -> new Clause(contract, "  ", 1)).hasMessageContaining("text");
    }

    @Test
    void detachingRemovesTheLinkToTheContract() {
        Clause clause = new Clause(contract, "Texto", 1);

        clause.detach();

        assertThat(clause.getContract()).isNull();
    }
}
