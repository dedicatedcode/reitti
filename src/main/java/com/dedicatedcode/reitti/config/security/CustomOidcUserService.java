package com.dedicatedcode.reitti.config.security;

import com.dedicatedcode.reitti.model.security.ExternalUser;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.AvatarService;
import com.dedicatedcode.reitti.service.UserService;
import com.dedicatedcode.reitti.service.security.ImageTypes;
import com.dedicatedcode.reitti.service.security.OutboundHttp;
import com.dedicatedcode.reitti.service.security.OutboundUrlValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Optional;

@Component
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private static final Logger log = LogManager.getLogger(CustomOidcUserService.class);
    private final OidcUserService defaultUserService = new OidcUserService();
    private final UserJdbcService userJdbcService;
    private final UserService userService;
    private final AvatarService avatarService;
    private final boolean registrationEnabled;
    private final boolean localLoginDisabled;
    private final RestTemplate restTemplate;
    private final OutboundUrlValidator outboundUrlValidator;

    public CustomOidcUserService(UserJdbcService userJdbcService,
                                 UserService userService,
                                 AvatarService avatarService,
                                 RestTemplate restTemplate,
                                 OutboundUrlValidator outboundUrlValidator,
                                 @Value("${reitti.security.oidc.registration.enabled}") boolean registrationEnabled,
                                 @Value("${reitti.security.local-login.disable:false}") boolean localLoginDisabled) {
        this.userJdbcService = userJdbcService;
        this.userService = userService;
        this.avatarService = avatarService;
        this.restTemplate = restTemplate;
        this.outboundUrlValidator = outboundUrlValidator;
        this.registrationEnabled = registrationEnabled;
        this.localLoginDisabled = localLoginDisabled;
    }

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = getDefaultUser(userRequest);
        String preferredUsername = userRequest.getIdToken().getPreferredUsername();
        if (preferredUsername == null) {
            preferredUsername = oidcUser.getPreferredUsername();
        }
        if (preferredUsername == null && oidcUser.getUserInfo() != null) {
            preferredUsername = oidcUser.getUserInfo().getPreferredUsername();
        }
        if (preferredUsername == null) {
            preferredUsername = oidcUser.getEmail();
        }
        if (preferredUsername == null && oidcUser.getFamilyName() != null && oidcUser.getGivenName() != null){
            preferredUsername = oidcUser.getGivenName().toLowerCase() + "." + oidcUser.getFamilyName().toLowerCase();
        }
        if (preferredUsername == null) {
            log.warn("No preferred username found for user: {}. Will fallback to OIDC subject", oidcUser);
            preferredUsername = userRequest.getIdToken().getSubject();
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
            if (byPreferredUserName.isPresent() && byPreferredUserName.get().getExternalId() != null
                    && !byPreferredUserName.get().getExternalId().isBlank()) {
                // The account already belongs to a different OIDC identity. Re-linking it would let anyone who can
                // pick a matching preferred_username/email at the identity provider take over the account.
                log.warn("Refusing OIDC login for [{}]: user [{}] is already linked to a different identity", oidcUserId, preferredUsername);
                throw new OAuth2AuthenticationException(new OAuth2Error("account_linked_to_other_identity"),
                        "User " + preferredUsername + " is already linked to a different identity");
            } else if (byPreferredUserName.isPresent()) {
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
            if (localLoginDisabled && user.getPassword() != null && !user.getPassword().isEmpty()) {
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
        return this.defaultUserService.loadUser(userRequest);
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

            // The picture URL comes from the identity provider (and is often editable by its users): validate it and
            // every redirect, cap the size and only store real images.
            URI uri = outboundUrlValidator.validate(avatarUrl);
            ResponseEntity<byte[]> response = null;
            for (int hop = 0; hop <= OutboundHttp.MAX_REDIRECTS; hop++) {
                response = restTemplate.execute(uri, HttpMethod.GET, null,
                        r -> ResponseEntity.status(r.getStatusCode())
                                .headers(r.getHeaders())
                                .body(OutboundHttp.readAtMost(r.getBody(),
                                        OutboundHttp.MAX_AVATAR_BYTES)));
                if (response == null || !response.getStatusCode().is3xxRedirection() || response.getHeaders().getLocation() == null) {
                    break;
                }
                uri = outboundUrlValidator.validate(uri.resolve(response.getHeaders().getLocation()));
            }

            byte[] avatarData = response != null && response.getStatusCode().is2xxSuccessful() ? response.getBody() : null;
            Optional<String> contentType = ImageTypes.detect(avatarData);
            if (contentType.isPresent()) {
                avatarService.updateAvatar(userId, contentType.get(), avatarData);
                log.info("Successfully saved avatar for user ID: {}", userId);
            } else {
                log.warn("No avatar image received from URL: {}", avatarUrl);
            }
        } catch (Exception e) {
            log.warn("Failed to download avatar from URL: {} for user ID: {}. {}", avatarUrl, userId, e.getMessage());
        }
    }
}

