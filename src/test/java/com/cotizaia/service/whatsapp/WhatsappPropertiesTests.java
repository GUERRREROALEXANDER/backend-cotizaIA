package com.cotizaia.service.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class WhatsappPropertiesTests {

    private static final String KEY = "x".repeat(WhatsappProperties.MIN_KEY_LENGTH);

    @Test
    void withoutAKeyTheServiceIsOff() {
        WhatsappProperties properties = new WhatsappProperties(null, null, null, null);

        assertThat(properties.serviceEnabled()).isFalse();
        assertThat(properties.serviceKey()).isEmpty();
        assertThat(properties.noticeLifetime()).isEqualTo(Duration.ofHours(1));
        assertThat(properties.siteUrl()).isEmpty();
        assertThat(properties.botNumber()).isEmpty();
    }

    @Test
    void aKeyShorterThanThirtyTwoCharactersIsRejected() {
        assertThatThrownBy(() -> new WhatsappProperties("short", null, null, null))
                .hasMessageContaining("WHATSAPP_SERVICE_KEY");
        assertThat(new WhatsappProperties(KEY, null, null, null).serviceEnabled()).isTrue();
    }

    @Test
    void theSiteLinkMustBeHttpAndLosesTrailingSlashes() {
        assertThat(new WhatsappProperties(null, null, "https://frontend-cotizaia-swart.vercel.app//", null).siteUrl())
                .isEqualTo("https://frontend-cotizaia-swart.vercel.app");
        assertThatThrownBy(() -> new WhatsappProperties(null, null, "javascript:alert(1)", null))
                .hasMessageContaining("WHATSAPP_SITE_URL");
    }

    @Test
    void theBotNumberIsNormalized() {
        assertThat(new WhatsappProperties(null, null, null, "+57 300 123 4567").botNumber())
                .isEqualTo("+573001234567");
        assertThatThrownBy(() -> new WhatsappProperties(null, null, null, "3001234567"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theNoticeLifetimeMustBePositive() {
        assertThatThrownBy(() -> new WhatsappProperties(null, Duration.ZERO, null, null))
                .hasMessageContaining("notice-lifetime");
    }
}
