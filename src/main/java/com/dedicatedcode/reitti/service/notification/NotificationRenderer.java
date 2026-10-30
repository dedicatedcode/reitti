package com.dedicatedcode.reitti.service.notification;

import com.dedicatedcode.reitti.model.notification.NotificationMessage;
import com.dedicatedcode.reitti.model.notification.NotificationType;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSettings;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.service.I18nService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class NotificationRenderer {
    private static final Logger log = LoggerFactory.getLogger(NotificationRenderer.class);

    private final I18nService i18n;
    private final UserSettingsJdbcService userSettingsJdbcService;

    public NotificationRenderer(I18nService i18n, UserSettingsJdbcService userSettingsJdbcService) {
        this.i18n = i18n;
        this.userSettingsJdbcService = userSettingsJdbcService;
    }

    public NotificationMessage render(User user, NotificationType type, Map<String, Object> params) {
        Map<String, Object> safeParams = params == null ? Map.of() : params;
        Locale locale = localeFor(user);

        String title = translate(type.titleKey(), locale, type.titleArgs(safeParams));

        Map<String, Object> inAppParams = type.htmlMessage() ? escapeHtmlParams(safeParams) : safeParams;
        String message = translate(type.messageKey(), locale, type.messageArgs(inAppParams));
        String externalMessage = translate(type.externalMessageKey(), locale, type.externalMessageArgs(safeParams));

        return new NotificationMessage(
                user.getId(),
                type.key(),
                type.severity(),
                title,
                message,
                externalMessage,
                safeParams,
                type.actionUrl(safeParams),
                type.dedupeKey(safeParams),
                Instant.now());
    }

    public Locale localeFor(User user) {
        try {
            UserSettings settings = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());
            if (settings.getSelectedLanguage() != null) {
                return settings.getSelectedLanguage().getLocale();
            }
        } catch (Exception e) {
            log.warn("Could not resolve language for user [{}], falling back to English", user.getId(), e);
        }
        return Locale.ENGLISH;
    }

    private String translate(String key, Locale locale, Object[] args) {
        try {
            return i18n.translate(key, locale, args);
        } catch (Exception e) {
            log.warn("Missing message key [{}] for notification rendering", key, e);
            return key;
        }
    }

    private Map<String, Object> escapeHtmlParams(Map<String, Object> params) {
        Map<String, Object> escaped = new LinkedHashMap<>();
        params.forEach((key, value) -> escaped.put(key, value == null ? null : HtmlUtils.htmlEscape(String.valueOf(value))));
        return escaped;
    }
}
