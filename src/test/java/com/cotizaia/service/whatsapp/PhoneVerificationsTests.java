package com.cotizaia.service.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PhoneVerificationsTests {

    private static final String PHONE = "+573001234567";

    private final MovableClock clock = new MovableClock();
    private final PhoneVerifications verifications = new PhoneVerifications(clock);

    @Test
    void theRightCodeProvesTheNumberOnce() {
        String code = verifications.start(1L, PHONE);

        assertThat(code).matches("\\d{6}");
        assertThat(verifications.confirm(1L, code)).contains(PHONE);
        assertThat(verifications.confirm(1L, code)).isEmpty();
    }

    @Test
    void aCodeIsBoundToTheUserWhoAskedForIt() {
        String code = verifications.start(1L, PHONE);

        assertThat(verifications.confirm(2L, code)).isEmpty();
        assertThat(verifications.confirm(1L, code)).contains(PHONE);
    }

    @Test
    void fiveWrongTriesBurnTheCode() {
        String code = verifications.start(1L, PHONE);
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int attempt = 0; attempt < PhoneVerifications.MAX_ATTEMPTS; attempt++) {
            assertThat(verifications.confirm(1L, wrong)).isEmpty();
        }
        assertThat(verifications.confirm(1L, code)).isEmpty();
    }

    @Test
    void anExpiredCodeIsRefused() {
        String code = verifications.start(1L, PHONE);
        clock.advance(PhoneVerifications.LIFETIME);

        assertThat(verifications.confirm(1L, code)).isEmpty();
    }

    @Test
    void aNewCodeWaitsOneMinuteAndSaysHowLong() {
        verifications.start(1L, PHONE);
        clock.advance(Duration.ofSeconds(20));

        assertThatThrownBy(() -> verifications.start(1L, PHONE))
                .isInstanceOfSatisfying(PhoneVerifications.TooManyCodesException.class,
                        error -> assertThat(error.retryAfterSeconds()).isBetween(40L, 41L));
        clock.advance(Duration.ofSeconds(41));
        assertThat(verifications.start(1L, PHONE)).matches("\\d{6}");
    }

    @Test
    void theNewestCodeReplacesTheOldOne() {
        String first = verifications.start(1L, PHONE);
        clock.advance(PhoneVerifications.COOLDOWN);
        String second = verifications.start(1L, "+573009999999");

        if (!first.equals(second)) {
            assertThat(verifications.confirm(1L, first)).isEmpty();
        }
        assertThat(verifications.confirm(1L, second)).contains("+573009999999");
    }

    @Test
    void atMostFiveCodesPerHourPerUser() {
        for (int sent = 0; sent < PhoneVerifications.HOURLY_LIMIT; sent++) {
            verifications.start(1L, "+57300000000" + sent);
            clock.advance(PhoneVerifications.COOLDOWN);
        }

        assertThatThrownBy(() -> verifications.start(1L, "+573009999999"))
                .isInstanceOf(PhoneVerifications.TooManyCodesException.class);
        clock.advance(Duration.ofHours(1));
        assertThat(verifications.start(1L, "+573009999999")).matches("\\d{6}");
    }

    @Test
    void aNumberCannotBeFloodedFromManyAccounts() {
        verifications.start(1L, PHONE);

        assertThatThrownBy(() -> verifications.start(2L, PHONE))
                .isInstanceOf(PhoneVerifications.TooManyCodesException.class);
    }

    @Test
    void phoneNumbersNeedTheCountryCode() {
        assertThat(PhoneNumbers.normalize("+57 300-123 4567")).isEqualTo(PHONE);
        assertThat(PhoneNumbers.normalize("+57 (300) 123.4567")).isEqualTo(PHONE);
        assertThatThrownBy(() -> PhoneNumbers.normalize("3001234567")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PhoneNumbers.normalize("+0573001234567"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PhoneNumbers.normalize(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(PhoneNumbers.masked(PHONE)).isEqualTo("*********4567");
    }

    static final class MovableClock extends Clock {

        private Instant now = Instant.parse("2026-11-20T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
