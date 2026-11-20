package com.cotizaia.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalExtra;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class RemoveExtraCommandTests {

    private final Proposal proposal = mock(Proposal.class);
    private final RemoveExtraCommand command = new RemoveExtraCommand(42L, "DISCOUNT", new BigDecimal("0.10"),
            new BigDecimal("-120000.00"));

    @Test
    void executeRemovesTheExtraWithThatId() {
        ProposalExtra other = mock(ProposalExtra.class);
        when(other.getId()).thenReturn(7L);
        ProposalExtra target = mock(ProposalExtra.class);
        when(target.getId()).thenReturn(42L);
        when(proposal.getAppliedExtras()).thenReturn(List.of(other, target));

        command.execute(proposal);

        verify(proposal).removeExtra(target);
    }

    @Test
    void executeFailsIfTheExtraIsGone() {
        when(proposal.getAppliedExtras()).thenReturn(List.of());

        assertThatThrownBy(() -> command.execute(proposal)).hasMessageContaining("42");
    }

    @Test
    void undoPutsTheSameExtraBack() {
        command.undo(proposal);

        verify(proposal).addExtra("DISCOUNT", new BigDecimal("0.10"), new BigDecimal("-120000.00"));
    }

    @Test
    void payloadKeepsTheIdAndValues() {
        assertThat(command.type()).isEqualTo("REMOVE_EXTRA");
        assertThat(command.payloadJson()).isEqualTo(
                "{\"extraId\":42,\"type\":\"DISCOUNT\",\"percentOrFixed\":\"0.10\",\"amount\":\"-120000.00\"}");
        assertThat(command.description()).isEqualTo("Extra DISCOUNT eliminado: -120000.00");
    }
}
