package com.cotizaia.service.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNumbersTests {

    @ParameterizedTest
    @ValueSource(strings = {"+573001234567", "+57 300 123 4567", "+57-300-123-4567", "+57 (300) 123.45.67"})
    void separatorsAreIgnored(String typed) {
        assertThat(PhoneNumbers.normalize(typed)).isEqualTo("+573001234567");
    }

    @ParameterizedTest
    @ValueSource(strings = {"3001234567", "573001234567", "+0573001234567", "+57", "+57300abc4567", ""})
    void numbersWithoutAValidCountryCodeAreRejected(String typed) {
        assertThatThrownBy(() -> PhoneNumbers.normalize(typed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("country code");
    }

    @Test
    void nullIsRejectedLikeAnEmptyNumber() {
        assertThatThrownBy(() -> PhoneNumbers.normalize(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void e164AllowsUpToFifteenDigits() {
        assertThat(PhoneNumbers.normalize("+123456789012345")).hasSize(16);
        assertThatThrownBy(() -> PhoneNumbers.normalize("+1234567890123456"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maskingKeepsOnlyTheLastFourDigits() {
        assertThat(PhoneNumbers.masked("+573001234567")).isEqualTo("*********4567");
    }

    @Test
    void shortOrMissingNumbersAreFullyMasked() {
        assertThat(PhoneNumbers.masked(null)).isEqualTo("****");
        assertThat(PhoneNumbers.masked("1234")).isEqualTo("****");
    }
}
