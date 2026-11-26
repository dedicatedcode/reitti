package com.dedicatedcode.reitti.service.extension;

import com.dedicatedcode.reitti.service.JdbcPropertyService;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class ExtensionCredentialService {
    private static final String KEY_PROPERTY = "extensions.credentials.aes-key";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final JdbcPropertyService propertyService;
    private final SecureRandom secureRandom = new SecureRandom();
    private volatile SecretKey key;

    public ExtensionCredentialService(JdbcPropertyService propertyService) {
        this.propertyService = propertyService;
    }

    public String encrypt(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (GeneralSecurityException e) {
            throw new ExtensionException("Extension credentials could not be protected", e);
        }
    }

    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            return null;
        }
        try {
            byte[] value = Base64.getDecoder().decode(encrypted);
            if (value.length <= IV_BYTES + TAG_BITS / 8) {
                throw new GeneralSecurityException("Invalid encrypted value");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, value, 0, IV_BYTES));
            return new String(cipher.doFinal(value, IV_BYTES, value.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new ExtensionException("Stored extension credentials cannot be decrypted", e);
        }
    }

    private SecretKey key() {
        SecretKey current = key;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (key == null) {
                String encoded = propertyService.getOrCreateProperty(KEY_PROPERTY, () -> {
                    try {
                        KeyGenerator generator = KeyGenerator.getInstance("AES");
                        generator.init(256);
                        return Base64.getEncoder().encodeToString(generator.generateKey().getEncoded());
                    } catch (GeneralSecurityException e) {
                        throw new ExtensionException("The extension encryption key could not be generated", e);
                    }
                });
                key = new SecretKeySpec(Base64.getDecoder().decode(encoded), "AES");
            }
            return key;
        }
    }
}
