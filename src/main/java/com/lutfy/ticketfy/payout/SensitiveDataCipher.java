package com.lutfy.ticketfy.payout;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SensitiveDataCipher {

    private static final String PREFIX = "v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SensitiveDataCipher(@Value("${ticketfy.payout.encryption-key}") String encodedKey,
                               @Value("${ticketfy.payout.development-encryption-key}") String developmentKey,
                               Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("prod")) && encodedKey.trim().equals(developmentKey.trim())) {
            throw new IllegalStateException(
                    "ticketfy.payout.encryption-key must not use the development key in the prod profile");
        }
        this.key = new SecretKeySpec(decodeKey(encodedKey), "AES");
    }

    public String encrypt(String plain) {
        if (plain == null) return null;
        try {
            var iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            var cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            var encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            var payload = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not encrypt sensitive data", ex);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) return null;
        if (!stored.startsWith(PREFIX)) {
            throw new IllegalStateException("Unsupported encrypted value format");
        }
        try {
            var payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            var cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES));
            var plain = cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Could not decrypt sensitive data", ex);
        }
    }

    private static byte[] decodeKey(String encodedKey) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encodedKey.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("ticketfy.payout.encryption-key must be valid base64", ex);
        }
        if (bytes.length != KEY_BYTES) {
            throw new IllegalStateException("ticketfy.payout.encryption-key must decode to 32 bytes");
        }
        return bytes;
    }
}
