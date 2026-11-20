package com.cotizaia.ingest.gmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GooglePushTokenVerifierTests {

    @Test
    void pushNeedsBothTheAudienceAndTheServiceAccount() {
        assertThatThrownBy(() -> new GooglePushTokenVerifier(null, "push@demo.iam.gserviceaccount.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GMAIL_PUSH_AUDIENCE");
        assertThatThrownBy(() -> new GooglePushTokenVerifier("https://api.demo/push", " "))
                .hasMessageContaining("GMAIL_PUSH_SERVICE_ACCOUNT");
    }

    @Test
    void aMalformedTokenIsRejectedWithoutThrowing() {
        GooglePushTokenVerifier verifier = new GooglePushTokenVerifier("https://api.demo/push",
                "push@demo.iam.gserviceaccount.com");

        assertThat(verifier.verify("not-a-jwt")).isFalse();
        assertThat(verifier.verify("")).isFalse();
    }

    @Test
    void keysComeFromGoogle() {
        assertThat(GooglePushTokenVerifier.GOOGLE_JWKS).startsWith("https://www.googleapis.com/");
    }
}
