package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WhatsappMessageTests {

    private static final Instant T0 = Instant.parse("2026-11-20T12:00:00Z");

    private static WhatsappMessage message() {
        return new WhatsappMessage("+573001234567", "Tu código es 123456", T0, T0.plus(Duration.ofMinutes(10)));
    }

    @Test
    void aClaimedMessageIsNotOfferedAgainWhileLeased() {
        WhatsappMessage message = message();

        assertThat(message.take(T0)).isTrue();
        assertThat(message.take(T0.plusSeconds(30))).isFalse();
        assertThat(message.take(T0.plus(WhatsappMessage.LEASE))).isTrue();
        assertThat(message.getAttempts()).isEqualTo(2);
    }

    @Test
    void afterThreeUnreportedAttemptsItFails() {
        WhatsappMessage message = message();
        Instant now = T0;
        for (int attempt = 0; attempt < WhatsappMessage.MAX_ATTEMPTS; attempt++) {
            assertThat(message.take(now)).isTrue();
            now = now.plus(WhatsappMessage.LEASE);
        }

        assertThat(message.take(now)).isFalse();
        assertThat(message.getStatus()).isEqualTo(WhatsappMessage.Status.FAILED);
        assertThat(message.getError()).isEqualTo("too many attempts");
    }

    @Test
    void anExpiredMessageIsNeverSent() {
        WhatsappMessage message = message();

        assertThat(message.take(T0.plus(Duration.ofMinutes(10)))).isFalse();
        assertThat(message.getStatus()).isEqualTo(WhatsappMessage.Status.FAILED);
        assertThat(message.getError()).isEqualTo("expired");
    }

    @Test
    void theOutcomeIsRecordedOnceAndTheBodyIsCleared() {
        WhatsappMessage message = message();
        message.take(T0);

        message.finish(null, T0.plusSeconds(5));
        message.finish("number is not on WhatsApp", T0.plusSeconds(6));

        assertThat(message.getStatus()).isEqualTo(WhatsappMessage.Status.SENT);
        assertThat(message.getError()).isNull();
        assertThat(message.getFinishedAt()).isEqualTo(T0.plusSeconds(5));
        assertThat(message.getBody()).doesNotContain("123456");
        assertThat(message.take(T0.plus(Duration.ofMinutes(2)))).isFalse();
    }

    @Test
    void aMessageNeedsAFutureExpiry() {
        assertThatThrownBy(() -> new WhatsappMessage("+573001234567", "hola", T0, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
