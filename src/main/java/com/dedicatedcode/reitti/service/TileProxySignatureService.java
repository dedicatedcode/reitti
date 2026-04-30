package com.dedicatedcode.reitti.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class TileProxySignatureService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    public TileProxySignatureService(@Value("${reitti.ui.tiles.proxy.signature-secret:}") String configuredSecret) {
        if (StringUtils.hasText(configuredSecret)) {
            this.secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
        } else {
            this.secret = new byte[32];
            new SecureRandom().nextBytes(this.secret);
        }
    }

    public String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(this.secret, HMAC_ALGORITHM));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign tile proxy URL", e);
        }
    }

    public boolean isValid(String value, String signature) {
        if (!StringUtils.hasText(signature)) {
            return false;
        }
        return MessageDigest.isEqual(sign(value).getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    }
}
