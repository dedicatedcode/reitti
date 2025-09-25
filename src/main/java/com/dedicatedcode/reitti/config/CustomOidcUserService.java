package com.dedicatedcode.reitti.config;

import com.dedicatedcode.reitti.model.security.ExternalUser;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.AvatarService;
import com.dedicatedcode.reitti.service.UserService;
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
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

@Component
public class CustomOidcUserService extends OidcUserService {

    private static final Logger log = LogManager.getLogger(CustomOidcUserService.class);
    private final UserJdbcService userJdbcService;
    private final UserService userService;
    private final AvatarService avatarService;
    private final boolean registrationEnabled;
    private final boolean localLoginDisabled;
    private final RestTemplate restTemplate;

    public CustomOidcUserService(UserJdbcService userJdbcService,
                                 UserService userService,
                                 AvatarService avatarService,
                                 RestTemplate restTemplate,
                                 @Value("${reitti.security.oidc.registration.enabled}") boolean registrationEnabled,
                                 @Value("${reitti.security.local-login.disable:false}") boolean localLoginDisabled) {
        this.userJdbcService = userJdbcService;
        this.userService = userService;
        this.avatarService = avatarService;
        this.restTemplate = restTemplate;
        this.registrationEnabled = registrationEnabled;
        this.localLoginDisabled = localLoginDisabled;
    }

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = getDefaultUser(userRequest);
        String preferredUsername = userRequest.getIdToken().getPreferredUsername();
        if (preferredUsername == null) {
            preferredUsername = oidcUser.getUserInfo().getPreferredUsername();
        }
        String oidcUserId = userRequest.getIdToken().getIssuer().toString() + ":" + userRequest.getIdToken().getSubject();

        String displayName = getDisplayName(oidcUser, preferredUsername);
        String avatarUrl = oidcUser.getPicture();
        String profileUrl = oidcUser.getProfile();

        Optional<User> existingUser;

        Optional<User> byOidcUserId = this.userJdbcService.findByExternalId(oidcUserId);
        if (byOidcUserId.isPresent()) {
            existingUser = byOidcUserId;
        } else {
            log.info("Oidc User not found for oidc id: [{}]. Will try to find it by preferred username [{}]", oidcUserId, preferredUsername);
            Optional<User> byPreferredUserName = this.userJdbcService.findByUsername(preferredUsername);
            if (byPreferredUserName.isPresent()) {
                log.info("found user by preferred username: [{}], will update username to [{}]", preferredUsername, oidcUserId);
                existingUser = Optional.of(byPreferredUserName.get().withUsername(preferredUsername).withExternalId(oidcUserId));
            } else {
                log.info("No user found for [{}] or [{}]", oidcUserId, preferredUsername);
                existingUser = Optional.empty();
            }
        }

        if  (existingUser.isPresent()) {
            User user = existingUser.get();
            if (localLoginDisabled && !user.getUsername().equals(preferredUsername)) {
                log.info("Updating username for user with id [{}] from [{}] to [{}]", user.getId(), user.getUsername(), preferredUsername);
                user = user.withUsername(preferredUsername);
            }
            if (localLoginDisabled && !user.getPassword().isEmpty()) {
                log.info("Reset password for user with id [{}]. Disabling local login.", user.getId());
                user = user.withPassword("");
            }
            user = user.withDisplayName(displayName)
                    .withProfileUrl(profileUrl)
                    .withExternalId(oidcUserId);

            User updatedUser = this.userJdbcService.updateUser(user);
            
            if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
                downloadAndSaveAvatar(user.getId(), avatarUrl);
            }
            
            return new ExternalUser(updatedUser, oidcUser);
        } else if (registrationEnabled) {
            User user = this.userService.createNewUser(preferredUsername, displayName, oidcUserId, profileUrl);

            if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
                downloadAndSaveAvatar(user.getId(), avatarUrl);
            }
            
            return new ExternalUser(user, oidcUser);
        } else {
            throw new UsernameNotFoundException("No internal user found for username: " + preferredUsername);
        }
    }

    // Made this package-local to allow mocking this out in testing. Do not touch!
    OidcUser getDefaultUser(OidcUserRequest userRequest) {
        return super.loadUser(userRequest);
    }

    private static String getDisplayName(OidcUser oidcUser, String preferredUsername) {
        String displayName = oidcUser.getFullName();
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = oidcUser.getGivenName() + " " + oidcUser.getFamilyName();
        }
        if (displayName.trim().isEmpty()) {
            displayName = preferredUsername;
        }
        return displayName;
    }

    private void downloadAndSaveAvatar(Long userId, String avatarUrl) {
        try {
            log.info("Downloading avatar from URL: {} for user ID: {}", avatarUrl, userId);
            
            byte[] avatarData = restTemplate.getForObject(URI.create(avatarUrl), byte[].class);
            
            if (avatarData != null && avatarData.length > 0) {
                String contentType = determineContentType(avatarUrl);
                avatarService.updateAvatar(userId, contentType, avatarData);
                log.info("Successfully saved avatar for user ID: {}", userId);
            } else {
                log.warn("No avatar data received from URL: {}", avatarUrl);
            }
        } catch (Exception e) {
            log.warn("Failed to download avatar from URL: {} for user ID: {}", avatarUrl, userId, e);
        }
    }
    
    private String determineContentType(String avatarUrl) {
        String url = avatarUrl.toLowerCase();
        if (url.contains(".png")) {
            return "image/png";
        } else if (url.contains(".gif")) {
            return "image/gif";
        } else if (url.contains(".webp")) {
            return "image/webp";
        } else {
            return "image/jpeg"; // Default fallback
        }
    }
}

