package com.cotizaia.crypto;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CryptoProperties.class)
public class CryptoConfiguration {

    @Bean
    public KeyProvider keyProvider(CryptoProperties properties) {
        if (properties == null || properties.getActiveKeyId() == null || properties.getActiveKeyId().isBlank()) {
            throw new IllegalArgumentException("crypto.active-key-id is required");
        }
        if (properties.getBlindIndexKey() == null || properties.getBlindIndexKey().isBlank()) {
            throw new IllegalArgumentException("crypto.blind-index-key is required");
        }
        Map<String, byte[]> keys = new HashMap<>();
        if (properties.getKeys() != null) {
            for (Map.Entry<String, String> entry : properties.getKeys().entrySet()) {
                String value = entry.getValue();
                if (value == null || value.isBlank()) {
                    throw new IllegalArgumentException("crypto key must not be blank");
                }
                byte[] raw;
                try {
                    raw = Base64.getDecoder().decode(value);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("crypto key must be base64", e);
                }
                keys.put(entry.getKey(), raw);
            }
        }
        // validate lengths through StaticKeyProvider
        return new StaticKeyProvider(keys, properties.getActiveKeyId());
    }

    @Bean
    public FieldCipher fieldCipher(KeyProvider keyProvider) {
        return new FieldCipher(keyProvider);
    }

    @Bean
    public BlindIndex blindIndex(CryptoProperties properties) {
        if (properties == null || properties.getBlindIndexKey() == null || properties.getBlindIndexKey().isBlank()) {
            throw new IllegalArgumentException("crypto.blind-index-key is required");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(properties.getBlindIndexKey());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("crypto blind-index-key must be base64", e);
        }
        if (raw.length != 32) {
            throw new IllegalArgumentException("crypto blind-index-key must be 32 bytes");
        }
        return new BlindIndex(raw);
    }
}