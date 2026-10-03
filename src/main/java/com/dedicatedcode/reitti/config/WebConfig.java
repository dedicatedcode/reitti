package com.dedicatedcode.reitti.config;

import com.dedicatedcode.reitti.service.security.OutboundHttp;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Configuration
@EnableScheduling
public class WebConfig implements WebMvcConfigurer {
    
    @Bean
    public RestTemplate restTemplate() {
        // Used for user supplied URLs (integrations, geocoders, avatars). Redirects are not followed, otherwise a
        // validated URL could bounce the request to an internal address.
        return new RestTemplate(OutboundHttp.noRedirectRequestFactory());
    }
    
    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver("reitti-language");
        resolver.setDefaultLocale(Locale.ENGLISH);
        resolver.setCookieMaxAge(Duration.of(365 * 24 * 60 * 60, ChronoUnit.SECONDS));
        resolver.setCookiePath("/");
        return resolver;
    }

}
