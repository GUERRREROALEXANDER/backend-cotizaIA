package com.cotizaia.ingest.gmail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class GmailPropertiesTests {

    private static GmailProperties withDefaults() {
        return new GmailProperties(false, null, null, null, null, false, false, false, 0, null, null);
    }

    @Test
    void theDefaultLabelIsCotizar() {
        assertThat(withDefaults().label()).isEqualTo("cotizar");
        assertThat(new GmailProperties(false, null, "  ", null, null, false, false, false, 0, null, null).label())
                .isEqualTo("cotizar");
    }

    @Test
    void pollingDefaultsToEveryMinuteAndTwentyMessages() {
        GmailProperties properties = withDefaults();

        assertThat(properties.pollInterval()).isEqualTo(Duration.ofMinutes(1));
        assertThat(properties.maxMessagesPerRun()).isEqualTo(20);
    }

    @Test
    void oauthAndPushAreNeverNull() {
        GmailProperties properties = withDefaults();

        assertThat(properties.oauth()).isNotNull();
        assertThat(properties.oauth().refreshToken()).isNull();
        assertThat(properties.push()).isNotNull();
        assertThat(properties.push().enabled()).isFalse();
    }

    @Test
    void explicitValuesAreKept() {
        GmailProperties properties = new GmailProperties(true, "agencia@demo.co", "presupuesto", 3L,
                Duration.ofMinutes(5), true, true, true, 50, null, null);

        assertThat(properties.label()).isEqualTo("presupuesto");
        assertThat(properties.pollInterval()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.maxMessagesPerRun()).isEqualTo(50);
        assertThat(properties.agencyId()).isEqualTo(3L);
    }
}
