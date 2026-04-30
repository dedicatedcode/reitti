package com.dedicatedcode.reitti.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;

@Service
public class RemoteTileUrlValidator {
    private static final long HOST_SAFETY_CACHE_MAX_SIZE = 50_000;
    private static final Duration HOST_SAFETY_CACHE_TTL = Duration.ofMinutes(10);

    private final boolean proxyLocalTileUrls;
    private final Cache<String, Boolean> hostSafetyCache;
    private final I18nService i18nService;

    @Autowired
    public RemoteTileUrlValidator(
            @Value("${reitti.ui.tiles.proxy-local-urls:false}") boolean proxyLocalTileUrls,
            I18nService i18nService) {
        this.proxyLocalTileUrls = proxyLocalTileUrls;
        this.i18nService = i18nService;
        this.hostSafetyCache = Caffeine.newBuilder()
                .maximumSize(HOST_SAFETY_CACHE_MAX_SIZE)
                .expireAfterWrite(HOST_SAFETY_CACHE_TTL)
                .build();
    }

    public RemoteTileUrlValidator(@Value("${reitti.ui.tiles.proxy-local-urls:false}") boolean proxyLocalTileUrls) {
        this.proxyLocalTileUrls = proxyLocalTileUrls;
        this.i18nService = null;
        this.hostSafetyCache = Caffeine.newBuilder()
                .maximumSize(HOST_SAFETY_CACHE_MAX_SIZE)
                .expireAfterWrite(HOST_SAFETY_CACHE_TTL)
                .build();
    }

    public URI requirePublicHttpUrl(String value, String fieldName) {
        URI uri = parseHttpUrl(value, fieldName);
        validatePublicHost(uri, fieldName);
        return uri;
    }

    public URI requireHttpUrl(String value, String fieldName) {
        return parseHttpUrl(value, fieldName);
    }

    public URI requirePublicHttpTemplate(String value, String fieldName) {
        return requirePublicHttpUrl(normalizeTemplateForParsing(value), fieldName);
    }

    public URI requireHttpTemplate(String value, String fieldName) {
        return requireHttpUrl(normalizeTemplateForParsing(value), fieldName);
    }

    public boolean isServerFetchAllowedUrl(String value) {
        try {
            requirePublicHttpUrl(value, "URL");
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isServerFetchAllowedTemplate(String value) {
        try {
            requirePublicHttpTemplate(value, "URL template");
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isValidLocalUrl(String value) {
        try {
            URI uri = parseHttpUrl(value, "URL");
            return isLocalHost(uri.getHost().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isValidLocalTemplate(String value) {
        return isValidLocalUrl(normalizeTemplateForParsing(value));
    }

    private URI parseHttpUrl(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message("js.map.settings.dialog.map-styles.error-url-required", fieldName + " is required.", fieldName));
        }

        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(message("js.map.settings.dialog.map-styles.error-url-invalid", fieldName + " must be a valid URL.", fieldName), e);
        }

        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException(message("js.map.settings.dialog.map-styles.error-url-scheme", fieldName + " must use HTTP or HTTPS.", fieldName));
        }
        if (!StringUtils.hasText(uri.getHost())) {
            throw new IllegalArgumentException(message("js.map.settings.dialog.map-styles.error-url-host", fieldName + " must include a host.", fieldName));
        }
        if (StringUtils.hasText(uri.getUserInfo())) {
            throw new IllegalArgumentException(message("js.map.settings.dialog.map-styles.error-url-credentials", fieldName + " must not contain embedded credentials.", fieldName));
        }
        return uri;
    }

    private void validatePublicHost(URI uri, String fieldName) {
        if (proxyLocalTileUrls) {
            return;
        }

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        boolean isPublic = hostSafetyCache.get(host, this::isPublicHost);
        if (!isPublic) {
            throw new IllegalArgumentException(message("js.map.settings.dialog.map-styles.error-url-private", fieldName + " must not target local or private network addresses.", fieldName));
        }
    }

    private boolean isPublicHost(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                return false;
            }
            for (InetAddress address : addresses) {
                if (!isPublicAddress(address)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isLocalHost(String host) {
        if ("localhost".equals(host) || host.endsWith(".localhost") || host.endsWith(".local")) {
            return true;
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (!isPublicAddress(address)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return !(first == 0
                    || first == 10
                    || first == 127
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168)
                    || (first == 198 && (second == 18 || second == 19))
                    || first >= 224);
        }
        if (bytes.length == 16) {
            int first = Byte.toUnsignedInt(bytes[0]);
            return (first & 0xfe) != 0xfc;
        }
        return false;
    }

    private String normalizeTemplateForParsing(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.trim().replaceAll("\\{[^}]+}", "0");
    }

    private String message(String key, String defaultMessage, Object... args) {
        return i18nService != null
                ? i18nService.translateWithDefault(key, defaultMessage, args)
                : defaultMessage;
    }
}
