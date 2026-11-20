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

class AddExtraCommandTests {

    private final Proposal proposal = mock(Proposal.class);
    private final AddExtraCommand command = new AddExtraCommand("URGENCY", new BigDecimal("0.25"),
            new BigDecimal("300000.00"));

    @Test
    void executeAddsTheExtraToTheProposal() {
        command.execute(proposal);

        verify(proposal).addExtra("URGENCY", new BigDecimal("0.25"), new BigDecimal("300000.00"));
    }

    @Test
    void undoRemovesTheExtraOfThatType() {
        ProposalExtra discount = mock(ProposalExtra.class);
        when(discount.getType()).thenReturn("DISCOUNT");
        ProposalExtra urgency = mock(ProposalExtra.class);
        when(urgency.getType()).thenReturn("URGENCY");
        when(proposal.getAppliedExtras()).thenReturn(List.of(discount, urgency));

        command.undo(proposal);

        verify(proposal).removeExtra(urgency);
    }

    @Test
    void undoFailsIfTheExtraIsAlreadyGone() {
        when(proposal.getAppliedExtras()).thenReturn(List.of());

        assertThatThrownBy(() -> command.undo(proposal)).hasMessageContaining("URGENCY");
    }

    @Test
    void payloadKeepsEverythingNeededToRebuildIt() {
        assertThat(command.type()).isEqualTo("ADD_EXTRA");
        assertThat(command.payloadJson())
                .isEqualTo("{\"type\":\"URGENCY\",\"percentOrFixed\":\"0.25\",\"amount\":\"300000.00\"}");
        assertThat(command.description()).isEqualTo("Extra URGENCY: 300000.00");
    }

    @Test
    void typeRateAndAmountAreRequired() {
        assertThatThrownBy(() -> new AddExtraCommand(" ", BigDecimal.ONE, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AddExtraCommand("URGENCY", null, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AddExtraCommand("URGENCY", BigDecimal.ONE, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
