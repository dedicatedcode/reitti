package com.dedicatedcode.reitti.config;

import com.dedicatedcode.reitti.model.security.ExternalUser;
import com.dedicatedcode.reitti.model.security.Role;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.AvatarService;
import com.dedicatedcode.reitti.test.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@IntegrationTest
public class CustomOidcUserServiceTest {

    @Autowired
    private UserJdbcService userJdbcService;

    @MockBean
    private AvatarService avatarService;

    @MockBean
    private RestTemplate restTemplate;

    private CustomOidcUserService customOidcUserService;

    @Mock
    private OidcUserRequest oidcUserRequest;

    @Mock
    private OidcIdToken idToken;

    private static final String ISSUER = "https://example.com";
    private static final String SUBJECT = "12345";
    private static final String PREFERRED_USERNAME = "testuser";
    private static final String OIDC_USER_ID = ISSUER + ":" + SUBJECT;
    private static final String DISPLAY_NAME = "Test User";
    private static final String AVATAR_URL = "https://example.com/avatar.jpg";
    private static final String PROFILE_URL = "https://example.com/profile";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Clear any existing test data
        userJdbcService.findByUsername(OIDC_USER_ID).ifPresent(user -> 
            userJdbcService.deleteUser(user.getId()));
        userJdbcService.findByUsername(PREFERRED_USERNAME).ifPresent(user -> 
            userJdbcService.deleteUser(user.getId()));
    }

    @Test
    void testLoadUser_NewUser_RegistrationEnabled() {
        // Given
        customOidcUserService = new CustomOidcUserService(userJdbcService, avatarService, true, false);
        OidcUser oidcUser = createOidcUser();
        setupMocks(oidcUser);
        
        byte[] avatarData = "fake-avatar-data".getBytes();
        when(restTemplate.getForObject(eq(URI.create(AVATAR_URL)), eq(byte[].class)))
            .thenReturn(avatarData);

        // When
        ExternalUser result = (ExternalUser) customOidcUserService.loadUser(oidcUserRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo(OIDC_USER_ID);
        assertThat(result.getDisplayName()).isEqualTo(DISPLAY_NAME);
        assertThat(result.getPassword()).isEmpty(); // Should be empty for OIDC users
        
        // Verify user was created in database
        Optional<User> savedUser = userJdbcService.findByUsername(OIDC_USER_ID);
        assertThat(savedUser).isPresent();
        assertThat(savedUser.get().getDisplayName()).isEqualTo(DISPLAY_NAME);
        assertThat(savedUser.get().getPassword()).isEmpty();
        assertThat(savedUser.get().getProfileUrl()).isEqualTo(PROFILE_URL);
        
        // Verify avatar was downloaded and saved
        verify(restTemplate).getForObject(eq(URI.create(AVATAR_URL)), eq(byte[].class));
        verify(avatarService).updateAvatar(eq(savedUser.get().getId()), eq("image/jpeg"), eq(avatarData));
    }

    @Test
    void testLoadUser_NewUser_RegistrationDisabled() {
        // Given
        customOidcUserService = new CustomOidcUserService(userJdbcService, avatarService, false, false);
        OidcUser oidcUser = createOidcUser();
        setupMocks(oidcUser);

        // When & Then
        assertThatThrownBy(() -> customOidcUserService.loadUser(oidcUserRequest))
            .isInstanceOf(UsernameNotFoundException.class)
            .hasMessageContaining("No internal user found for username: " + PREFERRED_USERNAME);
    }

    @Test
    void testLoadUser_ExistingUserByOidcId_LocalLoginDisabled() {
        // Given
        customOidcUserService = new CustomOidcUserService(userJdbcService, avatarService, true, true);
        
        // Create existing user with password
        User existingUser = new User()
            .withUsername(OIDC_USER_ID)
            .withDisplayName("Old Display Name")
            .withPassword("old-password")
            .withRole(Role.USER);
        existingUser = userJdbcService.createUser(existingUser);
        
        OidcUser oidcUser = createOidcUser();
        setupMocks(oidcUser);

        // When
        ExternalUser result = (ExternalUser) customOidcUserService.loadUser(oidcUserRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo(OIDC_USER_ID);
        assertThat(result.getDisplayName()).isEqualTo(DISPLAY_NAME);
        assertThat(result.getPassword()).isEmpty(); // Password should be cleared when local login disabled
        
        // Verify user was updated in database
        Optional<User> updatedUser = userJdbcService.findByUsername(OIDC_USER_ID);
        assertThat(updatedUser).isPresent();
        assertThat(updatedUser.get().getDisplayName()).isEqualTo(DISPLAY_NAME);
        assertThat(updatedUser.get().getPassword()).isEmpty(); // Password should be cleared
        assertThat(updatedUser.get().getProfileUrl()).isEqualTo(PROFILE_URL);
    }

    @Test
    void testLoadUser_ExistingUserByPreferredUsername_LocalLoginDisabled() {
        // Given
        customOidcUserService = new CustomOidcUserService(userJdbcService, avatarService, true, true);
        
        // Create existing user with preferred username and password
        User existingUser = new User()
            .withUsername(PREFERRED_USERNAME)
            .withDisplayName("Old Display Name")
            .withPassword("old-password")
            .withRole(Role.USER);
        existingUser = userJdbcService.createUser(existingUser);
        
        OidcUser oidcUser = createOidcUser();
        setupMocks(oidcUser);

        // When
        ExternalUser result = (ExternalUser) customOidcUserService.loadUser(oidcUserRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo(OIDC_USER_ID); // Username should be updated to OIDC ID
        assertThat(result.getDisplayName()).isEqualTo(DISPLAY_NAME);
        assertThat(result.getPassword()).isEmpty(); // Password should be cleared
        
        // Verify user was updated in database
        Optional<User> updatedUser = userJdbcService.findByUsername(OIDC_USER_ID);
        assertThat(updatedUser).isPresent();
        assertThat(updatedUser.get().getDisplayName()).isEqualTo(DISPLAY_NAME);
        assertThat(updatedUser.get().getPassword()).isEmpty(); // Password should be cleared
        
        // Verify old username no longer exists
        Optional<User> oldUser = userJdbcService.findByUsername(PREFERRED_USERNAME);
        assertThat(oldUser).isEmpty();
    }

    @Test
    void testLoadUser_ExistingUser_LocalLoginEnabled() {
        // Given
        customOidcUserService = new CustomOidcUserService(userJdbcService, avatarService, true, false);
        
        // Create existing user with password
        User existingUser = new User()
            .withUsername(OIDC_USER_ID)
            .withDisplayName("Old Display Name")
            .withPassword("existing-password")
            .withRole(Role.USER);
        existingUser = userJdbcService.createUser(existingUser);
        
        OidcUser oidcUser = createOidcUser();
        setupMocks(oidcUser);

        // When
        ExternalUser result = (ExternalUser) customOidcUserService.loadUser(oidcUserRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo(OIDC_USER_ID);
        assertThat(result.getDisplayName()).isEqualTo(DISPLAY_NAME);
        
        // Verify user was updated in database but password preserved
        Optional<User> updatedUser = userJdbcService.findByUsername(OIDC_USER_ID);
        assertThat(updatedUser).isPresent();
        assertThat(updatedUser.get().getDisplayName()).isEqualTo(DISPLAY_NAME);
        assertThat(updatedUser.get().getPassword()).isEqualTo("existing-password"); // Password should be preserved
        assertThat(updatedUser.get().getProfileUrl()).isEqualTo(PROFILE_URL);
    }

    @Test
    void testLoadUser_AvatarDownloadFailure() {
        // Given
        customOidcUserService = new CustomOidcUserService(userJdbcService, avatarService, true, false);
        OidcUser oidcUser = createOidcUser();
        setupMocks(oidcUser);
        
        // Mock RestTemplate to throw exception
        when(restTemplate.getForObject(eq(URI.create(AVATAR_URL)), eq(byte[].class)))
            .thenThrow(new RuntimeException("Network error"));

        // When
        ExternalUser result = (ExternalUser) customOidcUserService.loadUser(oidcUserRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo(OIDC_USER_ID);
        
        // Verify avatar service was not called due to download failure
        verify(avatarService, never()).updateAvatar(any(), any(), any());
    }

    @Test
    void testLoadUser_NoAvatarUrl() {
        // Given
        customOidcUserService = new CustomOidcUserService(userJdbcService, avatarService, true, false);
        OidcUser oidcUser = createOidcUserWithoutAvatar();
        setupMocks(oidcUser);

        // When
        ExternalUser result = (ExternalUser) customOidcUserService.loadUser(oidcUserRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo(OIDC_USER_ID);
        
        // Verify no avatar download was attempted
        verify(restTemplate, never()).getForObject(any(URI.class), eq(byte[].class));
        verify(avatarService, never()).updateAvatar(any(), any(), any());
    }

    private OidcUser createOidcUser() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", SUBJECT);
        claims.put("preferred_username", PREFERRED_USERNAME);
        claims.put("name", DISPLAY_NAME);
        claims.put("given_name", "Test");
        claims.put("family_name", "User");
        claims.put("picture", AVATAR_URL);
        claims.put("profile", PROFILE_URL);
        
        return new DefaultOidcUser(null, createIdToken(claims), claims);
    }

    private OidcUser createOidcUserWithoutAvatar() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", SUBJECT);
        claims.put("preferred_username", PREFERRED_USERNAME);
        claims.put("name", DISPLAY_NAME);
        claims.put("given_name", "Test");
        claims.put("family_name", "User");
        claims.put("profile", PROFILE_URL);
        
        return new DefaultOidcUser(null, createIdToken(claims), claims);
    }

    private OidcIdToken createIdToken(Map<String, Object> claims) {
        return new OidcIdToken(
            "token-value",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            claims
        );
    }

    private void setupMocks(OidcUser oidcUser) {
        when(oidcUserRequest.getIdToken()).thenReturn(idToken);
        when(idToken.getPreferredUsername()).thenReturn(PREFERRED_USERNAME);
        when(idToken.getIssuer()).thenReturn(URI.create(ISSUER));
        when(idToken.getSubject()).thenReturn(SUBJECT);
        
        // Mock the parent class behavior
        CustomOidcUserService spyService = spy(customOidcUserService);
        doReturn(oidcUser).when(spyService).loadUser(oidcUserRequest);
        customOidcUserService = spyService;
    }
}
