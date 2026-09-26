package com.househelper.converter;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final Logger log = LoggerFactory.getLogger(EncryptedStringConverter.class);
    private static final String PREFIX = "enc:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${app.encryption.key:}")
    private String base64Key;

    @PostConstruct
    void validateKey() {
        secretKey();
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }

        byte[] iv = new byte[IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(attribute.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException exception) {
            log.error("Failed to encrypt government ID data.", exception);
            throw new IllegalStateException("Could not encrypt government ID data.", exception);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        if (!dbData.startsWith(PREFIX)) {
            throw new IllegalStateException("Government ID data is not stored in the expected encrypted format.");
        }

        byte[] combined;
        try {
            combined = Base64.getDecoder().decode(dbData.substring(PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Stored government ID data has an invalid encrypted format.", exception);
        }
        if (combined.length <= IV_LENGTH) {
            throw new IllegalStateException("Stored government ID data is incomplete.");
        }

        byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
        byte[] encrypted = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            log.error("Failed to decrypt government ID data; verify the configured encryption key.", exception);
            throw new IllegalStateException("Could not decrypt government ID data; verify app.encryption.key.", exception);
        }
    }

    private SecretKeySpec secretKey() {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "Set app.encryption.key (a Base64-encoded 128-, 192-, or 256-bit AES key) before storing government ID data.");
        }

        try {
            byte[] key = Base64.getDecoder().decode(base64Key);
            if (key.length != 16 && key.length != 24 && key.length != 32) {
                throw new IllegalStateException("app.encryption.key must decode to 16, 24, or 32 bytes.");
            }
            return new SecretKeySpec(key, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("app.encryption.key must be valid Base64.", exception);
        }
    }
}
