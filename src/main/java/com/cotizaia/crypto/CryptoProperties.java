package com.cotizaia.crypto;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("crypto")
public class CryptoProperties {

    private String activeKeyId;
    private Map<String, String> keys = new HashMap<>();
    private String blindIndexKey;

    public String getActiveKeyId() {
        return activeKeyId;
    }

    public void setActiveKeyId(String activeKeyId) {
        this.activeKeyId = activeKeyId;
    }

    public Map<String, String> getKeys() {
        return keys;
    }

    public void setKeys(Map<String, String> keys) {
        this.keys = keys;
    }

    public String getBlindIndexKey() {
        return blindIndexKey;
    }

    public void setBlindIndexKey(String blindIndexKey) {
        this.blindIndexKey = blindIndexKey;
    }
}