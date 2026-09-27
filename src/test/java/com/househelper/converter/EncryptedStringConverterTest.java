package com.househelper.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EncryptedStringConverterTest {

    private static final String TEST_KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    @Test
    @DisplayName("Encrypts government ID values before persistence and decrypts them on read")
    void encryptsAndDecrypts() {
        EncryptedStringConverter converter = converterWithKey(TEST_KEY);
        String plaintext = "government-id-test-value";

        String databaseValue = converter.convertToDatabaseColumn(plaintext);

        assertTrue(databaseValue.startsWith("enc:"));
        assertNotEquals(plaintext, databaseValue);
        assertEquals(plaintext, converter.convertToEntityAttribute(databaseValue));
    }

    @Test
    @DisplayName("Uses a fresh initialization vector when encrypting the same value")
    void usesFreshInitializationVector() {
        EncryptedStringConverter converter = converterWithKey(TEST_KEY);

        String first = converter.convertToDatabaseColumn("same-government-id");
        String second = converter.convertToDatabaseColumn("same-government-id");

        assertNotEquals(first, second);
    }

    @Test
    @DisplayName("Rejects decrypting an existing value with a different encryption key")
    void rejectsDifferentKey() {
        EncryptedStringConverter encryptingConverter = converterWithKey(TEST_KEY);
        EncryptedStringConverter decryptingConverter = converterWithKey(Base64.getEncoder()
                .encodeToString("abcdef0123456789abcdef0123456789".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String databaseValue = encryptingConverter.convertToDatabaseColumn("government-id-test-value");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> decryptingConverter.convertToEntityAttribute(databaseValue));

        assertTrue(exception.getMessage().contains("verify app.encryption.key"));
    }

    private EncryptedStringConverter converterWithKey(String key) {
        EncryptedStringConverter converter = new EncryptedStringConverter();
        ReflectionTestUtils.setField(converter, "base64Key", key);
        return converter;
    }
}
