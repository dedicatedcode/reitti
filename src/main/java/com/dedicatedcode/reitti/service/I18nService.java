package com.dedicatedcode.reitti.service;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.Locale;
import java.util.ResourceBundle;

@Service
public class I18nService {
    private final MessageSource messageSource;

    public I18nService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String humanizeDuration(long seconds) {
        Duration duration = Duration.ofSeconds(seconds);
        return humanizeDuration(duration);
    }

    public String humanizeDuration(Duration duration) {
        return humanizeDuration(duration, LocaleContextHolder.getLocale());
    }

    public String humanizeDuration(Duration duration, Locale locale) {
        long hours = duration.toHours();
        long minutesPart = duration.toMinutesPart();

        Locale effectiveLocale = locale != null ? locale : Locale.ENGLISH;
        ResourceBundle bundle = ResourceBundle.getBundle("messages", effectiveLocale);

        if (hours > 0) {
            // Format for hours and minutes (e.g., "2 hours and 5 minutes")
            String pattern = bundle.getString("format.hours_minutes");
            // Note: The MessageFormat pattern is designed to handle the pluralization
            // and the exclusion of the minutes if minutesPart is 0.
            return new MessageFormat(pattern, effectiveLocale).format(new Object[]{hours, minutesPart});

        } else {
            // Format for minutes only (e.g., "65 minutes")
            long totalMinutes = duration.toMinutes();
            if (totalMinutes == 0) {
                return "Less than a minute"; // Handle very short durations
            }
            String pattern = bundle.getString("format.minutes_only");
            return new MessageFormat(pattern, effectiveLocale).format(new Object[]{totalMinutes});
        }
    }

    public String translate(String messageKey) {
        return messageSource.getMessage(messageKey, null, LocaleContextHolder.getLocale());
    }

    public String translate(String messageKey, Object... args) {
        return messageSource.getMessage(messageKey, args, LocaleContextHolder.getLocale());
    }

    public String translate(String messageKey, Locale locale, Object... args) {
        return messageSource.getMessage(messageKey, args, locale != null ? locale : Locale.ENGLISH);
    }
}
