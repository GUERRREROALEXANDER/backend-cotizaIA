package com.cotizaia.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** A missing or malformed key must stop the app at startup, never fall back to plaintext or a default key. */
class CryptoConfigurationTests {

    private static final String KEY = Base64.getEncoder().encodeToString(FieldCipherTests.bytes(3));

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CryptoConfiguration.class);

    @Test
    void startsWithValidKeys() {
        runner.withPropertyValues("crypto.active-key-id=k1", "crypto.keys.k1=" + KEY, "crypto.blind-index-key=" + KEY)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FieldCipher.class).hasSingleBean(BlindIndex.class);
                });
    }

    @Test
    void failsWithoutKeys() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWithoutBlindIndexKey() {
        runner.withPropertyValues("crypto.active-key-id=k1", "crypto.keys.k1=" + KEY)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenActiveKeyIsShort() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        runner.withPropertyValues("crypto.active-key-id=k1", "crypto.keys.k1=" + shortKey,
                "crypto.blind-index-key=" + KEY)
                .run(context -> assertThat(context).hasFailed());
    }
}
