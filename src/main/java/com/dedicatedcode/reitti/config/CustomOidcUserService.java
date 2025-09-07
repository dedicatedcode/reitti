package com.dedicatedcode.reitti.config;

import com.dedicatedcode.reitti.model.security.ExternalUser;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

    private static final Logger log = LogManager.getLogger(CustomOidcUserService.class);
    private final UserJdbcService userJdbcService;
    private final boolean registrationEnabled;
    private final boolean localLoginDisabled;

    public CustomOidcUserService(UserJdbcService userJdbcService,
                                 @Value("${reitti.security.oidc.registration.enabled}") boolean registrationEnabled,
                                 @Value("${reitti.security.local-login.disable:false}") boolean localLoginDisabled) {
        this.userJdbcService = userJdbcService;
        this.registrationEnabled = registrationEnabled;
        this.localLoginDisabled = localLoginDisabled;
    }

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        String preferredUsername = userRequest.getIdToken().getPreferredUsername();
        String oidcUserId = userRequest.getIdToken().getIssuer().toString() + ":" + userRequest.getIdToken().getSubject();

        String displayName = getDisplayName(oidcUser, preferredUsername);
        String avatarUrl = oidcUser.getPicture();
        String profileUrl = oidcUser.getProfile();

        Optional<User> existingUser;

        Optional<User> byOidcUserId = this.userJdbcService.findByUsername(oidcUserId);
        if (byOidcUserId.isPresent()) {
            existingUser = byOidcUserId;
        } else {
            log.info("Oidc User not found for oidc id: [{}]. Will try to find it by preferred username [{}]", oidcUserId, preferredUsername);
            Optional<User> byPreferredUserName = this.userJdbcService.findByUsername(preferredUsername);
            if (byPreferredUserName.isPresent()) {
                log.info("found user by preferred username: [{}], will update username to [{}]", preferredUsername, oidcUserId);
                existingUser = Optional.of(byPreferredUserName.get().withUsername(oidcUserId));
            } else {
                log.info("No user found for [{}] or [{}]", oidcUserId, preferredUsername);
                existingUser = Optional.empty();
            }
        }

        if  (existingUser.isPresent()) {
            User user = existingUser.get();
            if (localLoginDisabled && !user.getUsername().equals(oidcUserId)) {
                log.info("Updating username for user with id [{}] from [{}] to [{}]", user.getId(), user.getUsername(), preferredUsername);
                user = user.withUsername(oidcUserId);
            }
            if (localLoginDisabled && !user.getPassword().isEmpty()) {
                log.info("Reset password for user with id [{}]. Disabling local login.", user.getId());
                user = user.withPassword("");
            }
            user = user.withDisplayName(displayName).withProfileUrl(profileUrl);

            return new ExternalUser(this.userJdbcService.updateUser(user), oidcUser);
            //update workflow
        } else if (registrationEnabled) {
            User user = new User()
                    .withUsername(oidcUserId)
                    .withDisplayName(displayName)
                    .withProfileUrl(profileUrl)
                    .withPassword("");

            user = this.userJdbcService.createUser(user);
            return new ExternalUser(this.userJdbcService.updateUser(user), oidcUser);
            //new user workflow
        } else {
            throw new UsernameNotFoundException("No internal user found for username: " + preferredUsername);
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
                        XXX, currentUser.getRole(),
                    currentUser.getVersion()
                );
                user = userJdbcService.updateUser(updatedUser);
            } else {
                user = currentUser;
            }
        } else if (registrationEnabled) {
            user = userJdbcService.createUser(preferredUsername, displayName, "");
        } else {
            user = null;
        }

        if (user == null) {
        }
        return new ExternalUser(user, oidcUser);
    }

    private static String getDisplayName(OidcUser oidcUser, String preferredUsername) {
        // Extract display name from OIDC user
        String displayName = oidcUser.getFullName();
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = oidcUser.getGivenName() + " " + oidcUser.getFamilyName();
        }
        if (displayName.trim().isEmpty()) {
            displayName = preferredUsername;
        }
        return displayName;
    }
}

