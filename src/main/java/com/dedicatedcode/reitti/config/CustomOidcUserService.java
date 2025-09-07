package com.dedicatedcode.reitti.config;

import com.dedicatedcode.reitti.model.security.ExternalUser;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class CustomOidcUserService extends OidcUserService {

    private final UserJdbcService userJdbcService;
    private final boolean registrationEnabled;

    public CustomOidcUserService(UserJdbcService userJdbcService,
                                 @Value("${reitti.security.oidc.registration.enabled}") boolean registrationEnabled) {
        this.userJdbcService = userJdbcService;
        this.registrationEnabled = registrationEnabled;
    }

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        var oidcUser = super.loadUser(userRequest);
        var preferredUsername = userRequest.getIdToken().getPreferredUsername();
        
        // Extract display name from OIDC user
        var displayName = oidcUser.getFullName();
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = oidcUser.getGivenName() + " " + oidcUser.getFamilyName();
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = preferredUsername;
        }

        Optional<User> existingUser = userJdbcService.findByUsername(preferredUsername);

        User user;
        if (existingUser.isPresent()) {
            // Update user from OIDC provider
            User currentUser = existingUser.get();
            if (!displayName.equals(currentUser.getDisplayName())) {
                User updatedUser = new User(
                    currentUser.getId(),
                    currentUser.getUsername(),
                    currentUser.getPassword(),
                    displayName,
                    currentUser.getRole(),
                    currentUser.getVersion()
                );
                user = userJdbcService.updateUser(updatedUser);
            } else {
                user = currentUser;
            }
        } else if (registrationEnabled) {
            // Create a new user with empty password, names from OIDC
            user = userJdbcService.createUser(preferredUsername, displayName, "");
        } else {
            user = null;
        }

        if (user == null) {
            throw new UsernameNotFoundException("No internal user found for username: " + preferredUsername);
        }
        return new ExternalUser(user, oidcUser);
    }
}

