package com.dedicatedcode.reitti.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.regex.Pattern;

@Service
public class MapStyleUrlValidator {

    private static final Pattern TEMPLATE_PLACEHOLDER_PATTERN = Pattern.compile("\\{[^}]+}");

    private final I18nService i18nService;

    public MapStyleUrlValidator(I18nService i18nService) {
        this.i18nService = i18nService;
    }

    public URI requireHttpUrl(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw validationError("map.settings.dialog.map-styles.error-url-required", fieldName);
        }

        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(message("map.settings.dialog.map-styles.error-url-invalid", fieldName), e);
        }

        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw validationError("map.settings.dialog.map-styles.error-url-scheme", fieldName);
        }
        if (!StringUtils.hasText(uri.getHost())) {
            throw validationError("map.settings.dialog.map-styles.error-url-host", fieldName);
        }
        if (StringUtils.hasText(uri.getUserInfo())) {
            throw validationError("map.settings.dialog.map-styles.error-url-credentials", fieldName);
        }

        return uri;
    }

    public URI requireHttpTemplate(String value, String fieldName) {
        return requireHttpUrl(normalizeTemplateForParsing(value), fieldName);
    }

    private IllegalArgumentException validationError(String key, Object... args) {
        return new IllegalArgumentException(message(key, args));
    }

    private String message(String key, Object... args) {
        return i18nService.translate(key, args);
    }

    private static String normalizeTemplateForParsing(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return TEMPLATE_PLACEHOLDER_PATTERN.matcher(value.trim()).replaceAll("0");
    }
}
