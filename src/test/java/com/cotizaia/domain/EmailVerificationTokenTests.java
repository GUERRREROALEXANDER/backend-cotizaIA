package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class EmailVerificationTokenTests {

    private static final Instant EXPIRES = Instant.parse("2026-11-21T10:00:00Z");

    private final AppUser user = new AppUser(new Agency("Estudio Nariño Digital"), "Owner", "owner@demo.co",
            UserRole.OWNER);

    @Test
    void aFreshTokenCanBeRedeemedBeforeItExpires() {
        EmailVerificationToken token = new EmailVerificationToken(user, "hash", EXPIRES);

        assertThat(token.isUsed()).isFalse();
        assertThat(token.isRedeemable(EXPIRES.minusSeconds(1))).isTrue();
    }

    @Test
    void theTokenExpiresExactlyAtItsExpiryTime() {
        EmailVerificationToken token = new EmailVerificationToken(user, "hash", EXPIRES);

        assertThat(token.isExpired(EXPIRES.minusMillis(1))).isFalse();
        assertThat(token.isExpired(EXPIRES)).isTrue();
        assertThat(token.isRedeemable(EXPIRES)).isFalse();
    }

    @Test
    void userHashAndExpiryAreRequired() {
        assertThatThrownBy(() -> new EmailVerificationToken(null, "hash", EXPIRES))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailVerificationToken(user, " ", EXPIRES))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailVerificationToken(user, "hash", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepsOnlyTheHashOfTheToken() {
        EmailVerificationToken token = new EmailVerificationToken(user, "abc123", EXPIRES);

        assertThat(token.getTokenHash()).isEqualTo("abc123");
        assertThat(token.getUser()).isSameAs(user);
        assertThat(token.getUsedAt()).isNull();
    }
}
