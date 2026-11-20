package com.cotizaia.service.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotizaia.domain.WhatsappMessage;
import com.cotizaia.repository.WhatsappMessageRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class WhatsappOutboxTests {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    private final WhatsappMessageRepository messages = mock(WhatsappMessageRepository.class);
    private final WhatsappOutbox outbox = new WhatsappOutbox(messages, Clock.fixed(NOW, ZoneOffset.UTC));

    private static WhatsappMessage pending(Duration lifetime) {
        return new WhatsappMessage("+573001234567", "Hola", NOW.minusSeconds(5), NOW.plus(lifetime));
    }

    @Test
    void queuedMessagesExpireAfterTheirLifetime() {
        when(messages.save(any(WhatsappMessage.class))).thenAnswer(call -> call.getArgument(0));

        WhatsappMessage queued = outbox.queue("+573001234567", "Tu código es 123456", Duration.ofMinutes(10));

        assertThat(queued.getStatus()).isEqualTo(WhatsappMessage.Status.PENDING);
        assertThat(queued.getCreatedAt()).isEqualTo(NOW);
        assertThat(queued.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
    }

    @Test
    void claimTakesNoMoreThanTheLimit() {
        when(messages.lockByStatus(eq(WhatsappMessage.Status.PENDING), any(Pageable.class))).thenReturn(List.of(
                pending(Duration.ofHours(1)), pending(Duration.ofHours(1)), pending(Duration.ofHours(1))));

        assertThat(outbox.claim(2)).hasSize(2).allMatch(message -> message.getAttempts() == 1);
    }

    @Test
    void expiredMessagesAreNotHandedToTheSender() {
        WhatsappMessage stale = new WhatsappMessage("+573001234567", "Hola", NOW.minusSeconds(60),
                NOW.minusSeconds(1));
        when(messages.lockByStatus(eq(WhatsappMessage.Status.PENDING), any(Pageable.class)))
                .thenReturn(List.of(stale, pending(Duration.ofHours(1))));

        assertThat(outbox.claim(5)).hasSize(1);
        assertThat(stale.getStatus()).isEqualTo(WhatsappMessage.Status.FAILED);
    }

    @Test
    void finishReportsWhetherTheMessageExisted() {
        WhatsappMessage message = pending(Duration.ofHours(1));
        when(messages.lockById(1L)).thenReturn(Optional.of(message));
        when(messages.lockById(2L)).thenReturn(Optional.empty());

        assertThat(outbox.finish(1L, null)).isTrue();
        assertThat(message.getStatus()).isEqualTo(WhatsappMessage.Status.SENT);
        assertThat(outbox.finish(2L, null)).isFalse();
    }

    @Test
    void pendingCountsWhatIsStillWaiting() {
        when(messages.countByStatus(WhatsappMessage.Status.PENDING)).thenReturn(4L);

        assertThat(outbox.pending()).isEqualTo(4L);
    }
}
