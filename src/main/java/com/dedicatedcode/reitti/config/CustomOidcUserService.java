package com.dedicatedcode.reitti.config;

import com.dedicatedcode.reitti.model.security.ExternalUser;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import com.dedicatedcode.reitti.repository.UserJdbcService;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CustomOidcUserService extends OidcUserService {

    private final UserJdbcService userJdbcService;

    public CustomOidcUserService(UserJdbcService userJdbcService) {
        this.userJdbcService = userJdbcService;
    }

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        var preferredUsername = userRequest.getIdToken().getPreferredUsername();

        User user = userJdbcService.findByUsername(preferredUsername)
                .orElseThrow(() -> new UsernameNotFoundException("No internal user found for username: " + preferredUsername));

        return new ExternalUser(user, super.loadUser(userRequest));
    }
}

