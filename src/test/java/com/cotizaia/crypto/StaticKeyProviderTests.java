package com.cotizaia.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StaticKeyProviderTests {

    private static byte[] key(int fill) {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, (byte) fill);
        return key;
    }

    @Test
    void servesTheActiveKeyAndOlderKeysForRotation() {
        StaticKeyProvider provider = new StaticKeyProvider(Map.of("k1", key(1), "k2", key(2)), "k2");

        assertThat(provider.activeKeyId()).isEqualTo("k2");
        assertThat(provider.key("k1").getEncoded()).isEqualTo(key(1));
        assertThat(provider.key("k2").getAlgorithm()).isEqualTo("AES");
    }

    @Test
    void anUnknownKeyIdIsACryptoError() {
        StaticKeyProvider provider = new StaticKeyProvider(Map.of("k1", key(1)), "k1");

        assertThatThrownBy(() -> provider.key("k9")).isInstanceOf(CryptoException.class).hasMessageContaining("k9");
    }

    @Test
    void theActiveKeyMustBeOneOfTheKeys() {
        assertThatThrownBy(() -> new StaticKeyProvider(Map.of("k1", key(1)), "k2"))
                .hasMessageContaining("active key id not present");
    }

    @Test
    void keyIdsMustBeSimpleNames() {
        assertThatThrownBy(() -> new StaticKeyProvider(Map.of("k1", key(1)), "k:1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StaticKeyProvider(Map.of("bad id", key(1)), "k1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StaticKeyProvider(Map.of("k1", key(1)), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keysAreRequired() {
        assertThatThrownBy(() -> new StaticKeyProvider(null, "k1")).hasMessageContaining("keys must not be null");
        Map<String, byte[]> withNull = new HashMap<>();
        withNull.put("k1", null);
        assertThatThrownBy(() -> new StaticKeyProvider(withNull, "k1")).hasMessageContaining("32 bytes");
    }

    @Test
    void changingTheCallersArrayDoesNotChangeTheStoredKey() {
        byte[] original = key(7);
        StaticKeyProvider provider = new StaticKeyProvider(Map.of("k1", original), "k1");
        original[0] = 99;

        assertThat(provider.key("k1").getEncoded()[0]).isEqualTo((byte) 7);
    }
}
