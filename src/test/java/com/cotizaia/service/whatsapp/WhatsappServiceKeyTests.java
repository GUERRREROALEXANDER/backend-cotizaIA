package com.cotizaia.service.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WhatsappServiceKeyTests {

    private static final String KEY = "k".repeat(WhatsappProperties.MIN_KEY_LENGTH);

    private static WhatsappServiceKey keyCheck(String configured) {
        return new WhatsappServiceKey(new WhatsappProperties(configured, null, null, null));
    }

    @Test
    void theConfiguredKeyIsAccepted() {
        assertThatCode(() -> keyCheck(KEY).require(KEY)).doesNotThrowAnyException();
    }

    @Test
    void aWrongKeyIsRefusedWith401() {
        assertThatThrownBy(() -> keyCheck(KEY).require(KEY + "x"))
                .isInstanceOfSatisfying(WhatsappServiceKey.RefusedException.class,
                        refused -> assertThat(refused.status()).isEqualTo(401));
    }

    @Test
    void aMissingHeaderIsRefusedWith401() {
        assertThatThrownBy(() -> keyCheck(KEY).require(null))
                .isInstanceOfSatisfying(WhatsappServiceKey.RefusedException.class,
                        refused -> assertThat(refused.status()).isEqualTo(401));
    }

    @Test
    void withoutAConfiguredKeyEveryCallGets503() {
        assertThatThrownBy(() -> keyCheck("").require(KEY))
                .isInstanceOfSatisfying(WhatsappServiceKey.RefusedException.class,
                        refused -> assertThat(refused.status()).isEqualTo(503))
                .hasMessageContaining("not configured");
    }

    @Test
    void theConfiguredKeyIsTrimmedBeforeComparing() {
        assertThatCode(() -> keyCheck("  " + KEY + "  ").require(KEY)).doesNotThrowAnyException();
    }
}
