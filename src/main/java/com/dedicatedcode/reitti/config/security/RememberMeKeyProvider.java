package com.dedicatedcode.reitti.config.security;

import com.dedicatedcode.reitti.service.JdbcPropertyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Provides the secret used to sign remember-me cookies.
 * <p>
 * The signature of a remember-me cookie is {@code hash(username:expiry:password:key)}. With a key that is
 * public (it used to be a constant in the source code) a cookie can be forged for every account whose stored
 * password is known - and OIDC accounts are stored with an empty password. The key therefore has to be a
 * per-installation secret: it is generated once and persisted in the database so remember-me sessions survive restarts.
 */
@Component
public class RememberMeKeyProvider {
    private static final Logger log = LoggerFactory.getLogger(RememberMeKeyProvider.class);
    private static final String PROPERTY_KEY = "remember-me.key";

    private final String key;

    public RememberMeKeyProvider(JdbcPropertyService jdbcPropertyService) {
        this.key = jdbcPropertyService.getOrCreateProperty(PROPERTY_KEY, this::generateKey);
        log.info("Loaded remember-me key from reitti_properties");
    }

    public String getKey() {
        return key;
    }

    private String generateKey() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
