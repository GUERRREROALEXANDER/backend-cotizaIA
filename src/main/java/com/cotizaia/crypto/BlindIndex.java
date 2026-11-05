package com.cotizaia.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class BlindIndex {

    private final byte[] key;

    public BlindIndex(byte[] key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        this.key = key.clone();
    }

    public String hash(String value) {
        if (value == null) {
            return null;
        }
        try {
            String normalized = Normalizer.normalize(value.strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] h = mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new CryptoException("blind index failed", e);
        }
    }
}