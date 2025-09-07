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
        var preferredUsername = userRequest.getIdToken().getPreferredUsername();

        Optional<User> existingUser = userJdbcService.findByUsername(preferredUsername);

        User user;
        if (existingUser.isPresent()) {
            //update user from OIDC provider AI!
            user = existingUser.get();
        } else if (registrationEnabled) {
            //if oidc_register_users = true
            //create a new user with empty passord, names form oidc and the avatar binary
        } else {
            user = null;
        }

        if (user == null) {
            throw new UsernameNotFoundException("No internal user found for username: " + preferredUsername);
        }
        return new ExternalUser(user.orElse(null), super.loadUser(userRequest));
    }
}

