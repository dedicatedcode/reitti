package com.dedicatedcode.reitti.config;

import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.model.UserSettings;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserJdbcService userJdbcService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final LocaleResolver localeResolver;

    public CustomAuthenticationSuccessHandler(UserJdbcService userJdbcService, 
                                            UserSettingsJdbcService userSettingsJdbcService, 
                                            LocaleResolver localeResolver) {
        this.userJdbcService = userJdbcService;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.localeResolver = localeResolver;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, 
                                      Authentication authentication) throws IOException, ServletException {
        String username = authentication.getName();
        
        // Load user settings and set locale
        Optional<User> userOptional = userJdbcService.findByUsername(username);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            UserSettings userSettings = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());
            
            Locale userLocale = Locale.forLanguageTag(userSettings.getSelectedLanguage());
            localeResolver.setLocale(request, response, userLocale);
        }
        
        // Redirect to default success URL
        response.sendRedirect("/");
    }
}
