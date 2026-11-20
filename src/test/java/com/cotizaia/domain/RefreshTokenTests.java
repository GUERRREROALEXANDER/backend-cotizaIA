package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RefreshTokenTests {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    private final AppUser user = new AppUser(new Agency("Estudio Nariño Digital"), "Owner", "owner@demo.co",
            UserRole.OWNER);

    @Test
    void newTokenValuesAreUrlSafeAndUnique() {
        Set<String> values = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            values.add(RefreshToken.newTokenValue());
        }

        assertThat(values).hasSize(50).allMatch(value -> value.matches("[A-Za-z0-9_-]{43}"));
    }

    @Test
    void theHashIsStableSha256Hex() {
        assertThat(RefreshToken.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
                .isEqualTo(RefreshToken.hash("abc"))
                .isNotEqualTo(RefreshToken.hash("abd"));
    }

    @Test
    void revokingMarksTheTokenAsRevoked() {
        RefreshToken token = new RefreshToken(user, "family-1", "value", NOW.plusSeconds(60), NOW);

        assertThat(token.isRevoked()).isFalse();
        token.revoke(NOW, 9L);
        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getFamilyId()).isEqualTo("family-1");
    }

    @Test
    void itExpiresOnlyAfterItsExpiryTime() {
        RefreshToken token = new RefreshToken(user, "family-1", "value", NOW, NOW.minusSeconds(60));

        assertThat(token.isExpired(NOW)).isFalse();
        assertThat(token.isExpired(NOW.plusMillis(1))).isTrue();
    }

    @Test
    void everyFieldIsRequired() {
        assertThatThrownBy(() -> new RefreshToken(null, "f", "v", NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefreshToken(user, " ", "v", NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefreshToken(user, "f", "", NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefreshToken(user, "f", "v", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
