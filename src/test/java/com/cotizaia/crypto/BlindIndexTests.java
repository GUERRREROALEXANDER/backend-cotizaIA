package com.cotizaia.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Equality lookups over encrypted columns go through a keyed HMAC of the normalized value. */
class BlindIndexTests {

    private final BlindIndex index = new BlindIndex(FieldCipherTests.bytes(7));

    @Test
    void isDeterministicHexAndHidesTheValue() {
        String hash = index.hash("ana@demo.co");
        assertThat(hash).hasSize(64).matches("[0-9a-f]+").doesNotContain("ana");
        assertThat(index.hash("ana@demo.co")).isEqualTo(hash);
    }

    @Test
    void normalizesCaseAndSurroundingSpaces() {
        assertThat(index.hash("  Ana@Demo.CO ")).isEqualTo(index.hash("ana@demo.co"));
    }

    @Test
    void differentValuesAndDifferentKeysDiffer() {
        assertThat(index.hash("ana@demo.co")).isNotEqualTo(index.hash("ana2@demo.co"));
        assertThat(new BlindIndex(FieldCipherTests.bytes(8)).hash("ana@demo.co")).isNotEqualTo(index.hash("ana@demo.co"));
    }

    @Test
    void nullStaysNull() {
        assertThat(index.hash(null)).isNull();
    }
}
