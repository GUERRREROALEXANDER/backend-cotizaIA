package com.cotizaia.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Field-level encryption contract (docs/specs/01-encryption-at-rest.md): AES-256-GCM, a fresh IV per value,
 * the key id carried inside the ciphertext so old rows stay readable after rotation, and tampering detected.
 */
class FieldCipherTests {

    static final byte[] K1 = bytes(1);
    static final byte[] K2 = bytes(2);

    private final FieldCipher cipher = new FieldCipher(new StaticKeyProvider(Map.of("k1", K1), "k1"));

    @Test
    void stringRoundTripsAndNeverContainsThePlaintext() {
        String secret = "Restaurante El Sabor, reservas para 3 sedes";
        String stored = cipher.encrypt(secret);
        assertThat(stored).startsWith("v1:k1:").doesNotContain("Sabor");
        assertThat(cipher.decrypt(stored)).isEqualTo(secret);
    }

    @Test
    void samePlaintextEncryptsDifferentlyEachTime() {
        assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"));
    }

    @Test
    void nullStaysNullAndEmptyRoundTrips() {
        assertThat(cipher.encrypt((String) null)).isNull();
        assertThat(cipher.decrypt((String) null)).isNull();
        assertThat(cipher.decrypt(cipher.encrypt(""))).isEmpty();
    }

    @Test
    void handlesUnicode() {
        String text = "Diseño a medida, papelería y ñandú 🍔";
        assertThat(cipher.decrypt(cipher.encrypt(text))).isEqualTo(text);
    }

    @Test
    void tamperedCiphertextIsRejected() {
        String stored = cipher.encrypt("monto aprobado");
        String[] parts = stored.split(":", 3);
        byte[] raw = Base64.getDecoder().decode(parts[2]);
        raw[raw.length - 1] ^= 0x01;
        String tampered = parts[0] + ":" + parts[1] + ":" + Base64.getEncoder().encodeToString(raw);
        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(CryptoException.class);
    }

    @Test
    void valuesWrittenWithAnOldKeyStayReadableAfterRotation() {
        String old = cipher.encrypt("brief viejo");
        FieldCipher rotated = new FieldCipher(new StaticKeyProvider(Map.of("k1", K1, "k2", K2), "k2"));
        assertThat(rotated.decrypt(old)).isEqualTo("brief viejo");
        assertThat(rotated.encrypt("brief nuevo")).startsWith("v1:k2:");
    }

    @Test
    void unknownKeyIdIsRejected() {
        FieldCipher other = new FieldCipher(new StaticKeyProvider(Map.of("k2", K2), "k2"));
        String stored = other.encrypt("x");
        assertThatThrownBy(() -> cipher.decrypt(stored)).isInstanceOf(CryptoException.class);
    }

    @Test
    void plaintextThatWasNeverEncryptedIsRejectedNotPassedThrough() {
        assertThatThrownBy(() -> cipher.decrypt("Restaurante El Sabor")).isInstanceOf(CryptoException.class);
    }

    @Test
    void bytesRoundTripAndHideTheFileSignature() {
        byte[] pdf = "%PDF-1.7 contenido del cliente".getBytes(StandardCharsets.UTF_8);
        byte[] stored = cipher.encrypt(pdf);
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("%PDF").doesNotContain("cliente");
        assertThat(cipher.decrypt(stored)).isEqualTo(pdf);
        assertThat(cipher.encrypt((byte[]) null)).isNull();
    }

    @Test
    void tamperedBytesAreRejected() {
        byte[] stored = cipher.encrypt(new byte[] {1, 2, 3, 4});
        stored[stored.length - 1] ^= 0x01;
        assertThatThrownBy(() -> cipher.decrypt(stored)).isInstanceOf(CryptoException.class);
    }

    @Test
    void keyProviderRejectsKeysThatAreNot256Bits() {
        assertThatThrownBy(() -> new StaticKeyProvider(Map.of("k1", new byte[16]), "k1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StaticKeyProvider(Map.of("k1", K1), "missing"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StaticKeyProvider(Map.of("bad:id", K1), "bad:id"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static byte[] bytes(int fill) {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) fill);
        return key;
    }
}
