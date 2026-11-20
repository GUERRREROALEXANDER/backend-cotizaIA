package com.cotizaia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotizaia.domain.Contract;
import com.cotizaia.domain.Payment;
import com.cotizaia.domain.PaymentKind;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.payment.PaymentCharge;
import com.cotizaia.payment.PaymentGateway;
import com.cotizaia.payment.PaymentReceipt;
import com.cotizaia.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PaymentServiceTests {

    private static final Instant PAID = Instant.parse("2026-11-20T10:00:00Z");

    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentService service = new PaymentService(gateway, payments);

    private final Proposal proposal = mock(Proposal.class);
    private final Contract contract = mock(Contract.class);

    @BeforeEach
    void acceptedProposal() {
        when(proposal.getId()).thenReturn(7L);
        when(proposal.getStatus()).thenReturn(ProposalStatus.ACCEPTED);
        when(proposal.getTotal()).thenReturn(new BigDecimal("1000000.00"));
        when(contract.getProposal()).thenReturn(proposal);
        when(payments.saveAndFlush(any(Payment.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void chargesHalfOfTheTotalInPesos() {
        when(gateway.charge(any())).thenReturn(new PaymentReceipt("SIM-1", PAID, true));

        Payment payment = service.recordSimulatedDeposit(proposal, contract);

        ArgumentCaptor<PaymentCharge> charge = ArgumentCaptor.forClass(PaymentCharge.class);
        verify(gateway).charge(charge.capture());
        assertThat(charge.getValue().amount()).isEqualByComparingTo("500000.00");
        assertThat(charge.getValue().currency()).isEqualTo("COP");
        assertThat(payment.getProviderRef()).isEqualTo("SIM-1");
        assertThat(payment.getKind()).isEqualTo(PaymentKind.DEPOSIT);
    }

    @Test
    void aSecondDepositIsRefusedBeforeCharging() {
        when(payments.existsByProposalIdAndKind(7L, PaymentKind.DEPOSIT)).thenReturn(true);

        assertThatThrownBy(() -> service.recordSimulatedDeposit(proposal, contract))
                .hasMessageContaining("Deposit already exists");
        verify(gateway, never()).charge(any());
    }

    @Test
    void aRealReceiptIsNotAccepted() {
        when(gateway.charge(any())).thenReturn(new PaymentReceipt("REAL-1", PAID, false));

        assertThatThrownBy(() -> service.recordSimulatedDeposit(proposal, contract))
                .hasMessageContaining("Only simulated");
        verify(payments, never()).saveAndFlush(any());
    }

    @Test
    void anUnsavedProposalIsRejected() {
        Proposal unsaved = mock(Proposal.class);
        when(unsaved.getId()).thenReturn(null);

        assertThatThrownBy(() -> service.recordSimulatedDeposit(unsaved, contract))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recordSimulatedDeposit(null, contract))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findDepositLooksUpTheDepositKind() {
        when(payments.findByProposalIdAndKind(7L, PaymentKind.DEPOSIT)).thenReturn(Optional.empty());

        assertThat(service.findDeposit(7L)).isEmpty();
        verify(payments).findByProposalIdAndKind(7L, PaymentKind.DEPOSIT);
    }
}
