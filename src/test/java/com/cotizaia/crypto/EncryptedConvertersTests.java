package com.cotizaia.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EncryptedConvertersTests {

    private static final byte[] KEY = new byte[32];

    static {
        Arrays.fill(KEY, (byte) 3);
    }

    private final FieldCipher cipher = new FieldCipher(new StaticKeyProvider(Map.of("k1", KEY), "k1"));
    private final EncryptedStringConverter strings = new EncryptedStringConverter(cipher);
    private final EncryptedBytesConverter bytes = new EncryptedBytesConverter(cipher);

    @Test
    void textColumnsAreStoredEncryptedAndReadBack() {
        String column = strings.convertToDatabaseColumn("Panadería de Ipiales");

        assertThat(column).startsWith("v1:k1:").doesNotContain("Panadería");
        assertThat(strings.convertToEntityAttribute(column)).isEqualTo("Panadería de Ipiales");
    }

    @Test
    void binaryColumnsAreStoredEncryptedAndReadBack() {
        byte[] pdf = "%PDF-1.7 propuesta".getBytes(StandardCharsets.UTF_8);
        byte[] column = bytes.convertToDatabaseColumn(pdf);

        assertThat(new String(column, StandardCharsets.ISO_8859_1)).doesNotContain("%PDF");
        assertThat(bytes.convertToEntityAttribute(column)).isEqualTo(pdf);
    }

    @Test
    void nullColumnsStayNull() {
        assertThat(strings.convertToDatabaseColumn(null)).isNull();
        assertThat(strings.convertToEntityAttribute(null)).isNull();
        assertThat(bytes.convertToDatabaseColumn(null)).isNull();
        assertThat(bytes.convertToEntityAttribute(null)).isNull();
    }
}
