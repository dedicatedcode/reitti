package com.dedicatedcode.reitti.config.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Provides the secret used to sign remember-me cookies.
 * <p>
 * The signature of a remember-me cookie is {@code hash(username:expiry:password:key)}. With a key that is
 * public (it used to be a constant in the source code) a cookie can be forged for every account whose stored
 * password is known - and OIDC accounts are stored with an empty password. The key therefore has to be a
 * per-installation secret: it is taken from {@code reitti.security.remember-me.key} (env REMEMBER_ME_KEY) or,
 * if that is not set, generated once and persisted in the storage directory so sessions survive restarts.
 */
@Component
public class RememberMeKeyProvider {
    private static final Logger log = LoggerFactory.getLogger(RememberMeKeyProvider.class);
    static final String KEY_FILE_NAME = ".remember-me.key";
    private static final int MIN_CONFIGURED_KEY_LENGTH = 32;

    private final String key;

    public RememberMeKeyProvider(@Value("${reitti.security.remember-me.key:}") String configuredKey,
                                 @Value("${reitti.storage.path}") String storagePath) {
        this.key = resolveKey(configuredKey, storagePath);
    }

    public String getKey() {
        return key;
    }

    private static String resolveKey(String configuredKey, String storagePath) {
        if (configuredKey != null && !configuredKey.isBlank()) {
            if (configuredKey.length() < MIN_CONFIGURED_KEY_LENGTH) {
                throw new IllegalStateException("reitti.security.remember-me.key must be at least " + MIN_CONFIGURED_KEY_LENGTH + " characters long");
            }
            return configuredKey;
        }

        Path keyFile = Paths.get(storagePath, KEY_FILE_NAME);
        try {
            if (Files.isRegularFile(keyFile)) {
                String stored = Files.readString(keyFile, StandardCharsets.UTF_8).trim();
                if (stored.length() >= MIN_CONFIGURED_KEY_LENGTH) {
                    return stored;
                }
                log.warn("Remember-me key in [{}] is too short, generating a new one", keyFile);
            }
            String generated = generateKey();
            Files.createDirectories(keyFile.toAbsolutePath().getParent());
            Files.writeString(keyFile, generated, StandardCharsets.UTF_8);
            restrictPermissions(keyFile);
            log.info("Generated a new remember-me key in [{}]", keyFile);
            return generated;
        } catch (IOException e) {
            log.warn("Could not read or persist the remember-me key in [{}] ({}). Using a temporary key, remember-me logins will not survive a restart.", keyFile, e.getMessage());
            return generateKey();
        }
    }

    private static String generateKey() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void restrictPermissions(Path keyFile) {
        try {
            Files.setPosixFilePermissions(keyFile, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException e) {
            // non-POSIX file system, nothing we can do
        }
    }
}
