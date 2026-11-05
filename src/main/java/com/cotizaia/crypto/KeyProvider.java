package com.cotizaia.crypto;

import javax.crypto.SecretKey;

public interface KeyProvider {
    String activeKeyId();

    SecretKey key(String keyId);
}