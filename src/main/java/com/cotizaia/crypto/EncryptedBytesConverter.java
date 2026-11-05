package com.cotizaia.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

@Component
@Converter
public class EncryptedBytesConverter implements AttributeConverter<byte[], byte[]> {

    private final FieldCipher cipher;

    public EncryptedBytesConverter(FieldCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public byte[] convertToDatabaseColumn(byte[] attribute) {
        return cipher.encrypt(attribute);
    }

    @Override
    public byte[] convertToEntityAttribute(byte[] dbData) {
        return cipher.decrypt(dbData);
    }
}