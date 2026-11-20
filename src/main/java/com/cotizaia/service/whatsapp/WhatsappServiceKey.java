package com.cotizaia.service.whatsapp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Checks that a call comes from the WhatsApp service: it carries the shared WHATSAPP_SERVICE_KEY in X-API-Key.
 * The comparison takes the same time for any wrong key, so the key cannot be guessed byte by byte.
 */
public class WhatsappServiceKey {

    /** Why a call was refused: 503 when no key is configured, 401 when the key is wrong. */
    public static class RefusedException extends RuntimeException {

        private final int status;

        RefusedException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    private final WhatsappProperties properties;

    public WhatsappServiceKey(WhatsappProperties properties) {
        this.properties = properties;
    }

    public void require(String given) {
        if (!properties.serviceEnabled()) {
            throw new RefusedException(503, "WhatsApp service is not configured");
        }
        byte[] expected = properties.serviceKey().getBytes(StandardCharsets.UTF_8);
        byte[] actual = given == null ? new byte[0] : given.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new RefusedException(401, "Invalid service key");
        }
    }
}
