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
        long hours = duration.toHours();
        long minutesPart = duration.toMinutesPart();

        Locale locale = LocaleContextHolder.getLocale();
        ResourceBundle bundle = ResourceBundle.getBundle("messages", locale);

        if (hours > 0) {
            // Format for hours and minutes (e.g., "2 hours and 5 minutes")
            String pattern = bundle.getString("format.hours_minutes");
            // Note: The MessageFormat pattern is designed to handle the pluralization
            // and the exclusion of the minutes if minutesPart is 0.
            return new MessageFormat(pattern, locale).format(new Object[]{hours, minutesPart});

        } else {
            // Format for minutes only (e.g., "65 minutes")
            long totalMinutes = duration.toMinutes();
            if (totalMinutes == 0) {
                return "Less than a minute"; // Handle very short durations
            }
            String pattern = bundle.getString("format.minutes_only");
            return new MessageFormat(pattern, locale).format(new Object[]{totalMinutes});
        }
    }
}
