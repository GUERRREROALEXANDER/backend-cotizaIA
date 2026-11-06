package com.cotizaia.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class FieldCipher {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final byte VERSION = 0x01;

    private final KeyProvider keys;
    private final SecureRandom random = new SecureRandom();

    public FieldCipher(KeyProvider keys) {
        this.keys = keys;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            String keyId = keys.activeKeyId();
            SecretKey key = keys.key(keyId);
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_LENGTH + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_LENGTH);
            System.arraycopy(ct, 0, out, IV_LENGTH, ct.length);
            return "v1:" + keyId + ":" + Base64.getEncoder().encodeToString(out);
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        String[] parts = stored.split(":", 3);
        if (parts.length != 3 || !"v1".equals(parts[0])) {
            throw new CryptoException("invalid ciphertext format");
        }
        String keyId = parts[1];
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new CryptoException("invalid base64", e);
        }
        if (raw.length < IV_LENGTH + (TAG_BITS / 8)) {
            throw new CryptoException("ciphertext too short");
        }
        byte[] iv = Arrays.copyOfRange(raw, 0, IV_LENGTH);
        byte[] ct = Arrays.copyOfRange(raw, IV_LENGTH, raw.length);
        try {
            SecretKey key = keys.key(keyId);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = cipher.doFinal(ct);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("decryption failed", e);
        }
    }

    public byte[] encrypt(byte[] plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            String keyId = keys.activeKeyId();
            byte[] keyIdBytes = keyId.getBytes(StandardCharsets.UTF_8);
            if (keyIdBytes.length > 255) {
                throw new CryptoException("key id too long");
            }
            SecretKey key = keys.key(keyId);
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext);
            byte[] out = new byte[1 + 1 + keyIdBytes.length + IV_LENGTH + ct.length];
            int pos = 0;
            out[pos++] = VERSION;
            out[pos++] = (byte) keyIdBytes.length;
            System.arraycopy(keyIdBytes, 0, out, pos, keyIdBytes.length);
            pos += keyIdBytes.length;
            System.arraycopy(iv, 0, out, pos, IV_LENGTH);
            pos += IV_LENGTH;
            System.arraycopy(ct, 0, out, pos, ct.length);
            return out;
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("encryption failed", e);
        }
    }

    public byte[] decrypt(byte[] stored) {
        if (stored == null) {
            return null;
        }
        if (stored.length < 1 + 1 + 1 + IV_LENGTH + (TAG_BITS / 8)) {
            throw new CryptoException("ciphertext too short");
        }
        if (stored[0] != VERSION) {
            throw new CryptoException("invalid version");
        }
        int kidLen = stored[1] & 0xFF;
        if (stored.length < 2 + kidLen + IV_LENGTH + (TAG_BITS / 8)) {
            throw new CryptoException("ciphertext too short");
        }
        String keyId = new String(stored, 2, kidLen, StandardCharsets.UTF_8);
        int pos = 2 + kidLen;
        byte[] iv = Arrays.copyOfRange(stored, pos, pos + IV_LENGTH);
        pos += IV_LENGTH;
        byte[] ct = Arrays.copyOfRange(stored, pos, stored.length);
        try {
            SecretKey key = keys.key(keyId);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(ct);
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("decryption failed", e);
        }
    }
}