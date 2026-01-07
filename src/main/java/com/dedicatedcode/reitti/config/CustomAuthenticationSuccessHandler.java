package com.dedicatedcode.reitti.config;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSettings;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.service.ContextPathHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserJdbcService userJdbcService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final LocaleResolver localeResolver;
    private final ContextPathHolder contextPathHolder;

    public CustomAuthenticationSuccessHandler(UserJdbcService userJdbcService,
                                              UserSettingsJdbcService userSettingsJdbcService,
                                              LocaleResolver localeResolver, ContextPathHolder contextPathHolder) {
        this.userJdbcService = userJdbcService;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.localeResolver = localeResolver;
        this.contextPathHolder = contextPathHolder;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String username = authentication.getName();
        
        // Load user settings and set locale
        Optional<User> userOptional = userJdbcService.findByUsername(username);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            UserSettings userSettings = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());
            
            Locale userLocale = userSettings.getSelectedLanguage().getLocale();
            localeResolver.setLocale(request, response, userLocale);
        }
        
        // Redirect to default success URL
        response.sendRedirect(contextPathHolder.getContextPath() + "/");
    }
}
