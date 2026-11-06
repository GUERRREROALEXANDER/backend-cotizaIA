package com.cotizaia.crypto;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.crypto.spec.SecretKeySpec;

public class StaticKeyProvider implements KeyProvider {

    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");

    private final Map<String, byte[]> keys;
    private final String activeKeyId;

    public StaticKeyProvider(Map<String, byte[]> keys, String activeKeyId) {
        if (activeKeyId == null || activeKeyId.isBlank() || !ID_PATTERN.matcher(activeKeyId).matches()) {
            throw new IllegalArgumentException("invalid active key id");
        }
        if (keys == null) {
            throw new IllegalArgumentException("keys must not be null");
        }
        Map<String, byte[]> copy = new HashMap<>();
        for (Map.Entry<String, byte[]> entry : keys.entrySet()) {
            String id = entry.getKey();
            if (id == null || id.isBlank() || !ID_PATTERN.matcher(id).matches()) {
                throw new IllegalArgumentException("invalid key id: " + id);
            }
            byte[] key = entry.getValue();
            if (key == null || key.length != 32) {
                throw new IllegalArgumentException("key must be exactly 32 bytes");
            }
            copy.put(id, key.clone());
        }
        if (!copy.containsKey(activeKeyId)) {
            throw new IllegalArgumentException("active key id not present");
        }
        this.keys = Map.copyOf(copy);
        this.activeKeyId = activeKeyId;
    }

    @Override
    public String activeKeyId() {
        return activeKeyId;
    }

    @Override
    public javax.crypto.SecretKey key(String keyId) {
        byte[] key = keys.get(keyId);
        if (key == null) {
            throw new CryptoException("unknown key id: " + keyId);
        }
        return new SecretKeySpec(key.clone(), "AES");
    }
}