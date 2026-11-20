package com.cotizaia.service.whatsapp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Six-digit codes that prove someone holds the WhatsApp number they typed. Kept in memory: a restart only means
 * asking for a new code. The limits protect the number's holder too, since anyone can type a stranger's number and
 * each request messages it: one code per minute and five per hour, counted per user and per number.
 */
public class PhoneVerifications {

    public static final Duration LIFETIME = Duration.ofMinutes(10);
    public static final int MAX_ATTEMPTS = 5;
    public static final Duration COOLDOWN = Duration.ofSeconds(60);
    public static final int HOURLY_LIMIT = 5;
    private static final Duration HOUR = Duration.ofHours(1);

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<Long, Pending> waiting = new HashMap<>();
    private final Map<String, Deque<Instant>> sent = new HashMap<>();

    public PhoneVerifications(Clock clock) {
        this.clock = clock;
    }

    /** Thrown when a new code would break the limits; says how long to wait. */
    public static class TooManyCodesException extends RuntimeException {

        private final long retryAfterSeconds;

        public TooManyCodesException(long retryAfterSeconds) {
            super("Too many codes requested; try again in " + retryAfterSeconds + " s");
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    private record Pending(String phone, String code, Instant expiresAt, int attempts) {
    }

    /** A new code for this user and number; it replaces any earlier one. */
    public synchronized String start(Long userId, String phone) {
        Instant now = clock.instant();
        List<String> keys = List.of("user:" + userId, "phone:" + phone);
        long wait = 0;
        for (String key : keys) {
            wait = Math.max(wait, waitFor(key, now));
        }
        if (wait > 0) {
            throw new TooManyCodesException(wait);
        }
        for (String key : keys) {
            sent.computeIfAbsent(key, ignored -> new ArrayDeque<>()).addLast(now);
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        waiting.put(userId, new Pending(phone, code, now.plus(LIFETIME), 0));
        return code;
    }

    /**
     * The number this code proves, once. Too many wrong tries burn the code, so six digits cannot be guessed.
     */
    public synchronized Optional<String> confirm(Long userId, String code) {
        Pending pending = waiting.get(userId);
        if (pending == null || code == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(pending.expiresAt())) {
            waiting.remove(userId);
            return Optional.empty();
        }
        if (MessageDigest.isEqual(code.getBytes(StandardCharsets.UTF_8),
                pending.code().getBytes(StandardCharsets.UTF_8))) {
            waiting.remove(userId);
            return Optional.of(pending.phone());
        }
        if (pending.attempts() + 1 >= MAX_ATTEMPTS) {
            waiting.remove(userId);
        } else {
            waiting.put(userId, new Pending(pending.phone(), pending.code(), pending.expiresAt(),
                    pending.attempts() + 1));
        }
        return Optional.empty();
    }

    /** Seconds until this key may receive another code; 0 when it may now. */
    private long waitFor(String key, Instant now) {
        Deque<Instant> recent = sent.get(key);
        if (recent == null) {
            return 0;
        }
        while (!recent.isEmpty() && !recent.peekFirst().plus(HOUR).isAfter(now)) {
            recent.removeFirst();
        }
        if (recent.isEmpty()) {
            sent.remove(key);
            return 0;
        }
        if (recent.size() >= HOURLY_LIMIT) {
            return Duration.between(now, recent.peekFirst().plus(HOUR)).toSeconds() + 1;
        }
        Instant last = recent.peekLast();
        if (last.plus(COOLDOWN).isAfter(now)) {
            return Duration.between(now, last.plus(COOLDOWN)).toSeconds() + 1;
        }
        return 0;
    }
}
