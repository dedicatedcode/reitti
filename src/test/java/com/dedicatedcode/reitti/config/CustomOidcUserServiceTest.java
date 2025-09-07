package com.dedicatedcode.reitti.config;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.security.ExternalUser;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.AvatarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestTemplate;

import java.net.MalformedURLException;
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

    @MockitoBean
    private AvatarService avatarService;

    @MockitoBean
    private RestTemplate restTemplate;

    private CustomOidcUserService customOidcUserService;

    private static final String ISSUER = "https://localhost";
    private static final String SUBJECT = "12345";
    private static final String PREFERRED_USERNAME = "testuser";
    private static final String EXTERNAL_ID = ISSUER + ":" + SUBJECT;
    private static final String DISPLAY_NAME = "Test User";
    private static final String AVATAR_URL = "https://localhost/avatar.jpg";
    private static final String PROFILE_URL = "https://localhost/profile";

    @BeforeEach
    void setUp() {

        // Clear any existing test data
        userJdbcService.findByExternalId(EXTERNAL_ID).ifPresent(user -> 
            userJdbcService.deleteUser(user.getId()));
        userJdbcService.findByUsername(PREFERRED_USERNAME).ifPresent(user -> 
            userJdbcService.deleteUser(user.getId()));
    }

    @Test
    void testLoadUser_NewUser_RegistrationEnabled() throws MalformedURLException {
        // Given
        customOidcUserService = spy(new CustomOidcUserService(userJdbcService, avatarService, restTemplate, true, false));
        OidcUserRequest oidcUserRequest = createOidcUserRequest();
        OidcUser mockOidcUser = createMockOidcUser();
        
        doReturn(mockOidcUser).when(customOidcUserService).getDefaultUser(oidcUserRequest);
        
        byte[] avatarData = "fake-avatar-data".getBytes();
        when(restTemplate.getForObject(eq(URI.create(AVATAR_URL)), eq(byte[].class)))
            .thenReturn(avatarData);

        // When
        ExternalUser result = (ExternalUser) customOidcUserService.loadUser(oidcUserRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo(PREFERRED_USERNAME);
        assertThat(result.getDisplayName()).isEqualTo(DISPLAY_NAME);
        assertThat(result.getPassword()).isEmpty(); // Should be empty for OIDC users
        
        // Verify user was created in the database
        Optional<User> savedUser = userJdbcService.findByUsername(PREFERRED_USERNAME);
        assertThat(savedUser).isPresent();
        assertThat(savedUser.get().getDisplayName()).isEqualTo(DISPLAY_NAME);
        assertThat(savedUser.get().getPassword()).isEmpty();
        assertThat(savedUser.get().getProfileUrl()).isEqualTo(PROFILE_URL);
        assertThat(savedUser.get().getExternalId()).isEqualTo(EXTERNAL_ID);
        
        // Verify avatar was downloaded and saved
        verify(restTemplate).getForObject(eq(URI.create(AVATAR_URL)), eq(byte[].class));
        verify(avatarService).updateAvatar(eq(savedUser.get().getId()), eq("image/jpeg"), eq(avatarData));
    }

    @Test
    void testLoadUser_NewUser_RegistrationDisabled() throws MalformedURLException {
        // Given
        customOidcUserService = spy(new CustomOidcUserService(userJdbcService, avatarService, restTemplate, false, false));
        OidcUserRequest oidcUserRequest = createOidcUserRequest();
        OidcUser mockOidcUser = createMockOidcUser();
        
        doReturn(mockOidcUser).when(customOidcUserService).getDefaultUser(oidcUserRequest);

        // When & Then
        assertThatThrownBy(() -> customOidcUserService.loadUser(oidcUserRequest))
            .isInstanceOf(UsernameNotFoundException.class)
            .hasMessageContaining("No internal user found for username: " + PREFERRED_USERNAME);
    }

    @Test
    void testLoadUser_ExistingUserByOidcId_LocalLoginDisabled() throws MalformedURLException {
        // Given
        customOidcUserService = spy(new CustomOidcUserService(userJdbcService, avatarService, restTemplate, true, true));
        
        // Create existing user with password
        userJdbcService.createUser(new User()
            .withUsername(PREFERRED_USERNAME)
            .withExternalId(EXTERNAL_ID)
            .withDisplayName("Old Display Name")
            .withPassword("old-password")
            .withRole(Role.USER));
        
        OidcUserRequest oidcUserRequest = createOidcUserRequest();
        OidcUser mockOidcUser = createMockOidcUser();
        
        doReturn(mockOidcUser).when(customOidcUserService).getDefaultUser(oidcUserRequest);

        // When
        ExternalUser result = (ExternalUser) customOidcUserService.loadUser(oidcUserRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo(PREFERRED_USERNAME);
        assertThat(result.getDisplayName()).isEqualTo(DISPLAY_NAME);
        assertThat(result.getPassword()).isEmpty(); // Password should be cleared when local login disabled
        
        // Verify user was updated in the database
        Optional<User> updatedUser = userJdbcService.findByUsername(PREFERRED_USERNAME);
        assertThat(updatedUser).isPresent();
        assertThat(updatedUser.get().getDisplayName()).isEqualTo(DISPLAY_NAME);
        assertThat(updatedUser.get().getPassword()).isEmpty(); // Password should be cleared
        assertThat(updatedUser.get().getProfileUrl()).isEqualTo(PROFILE_URL);
        assertThat(updatedUser.get().getExternalId()).isEqualTo(EXTERNAL_ID);
    }

    @Test
    void testLoadUser_ExistingUserByPreferredUsername_LocalLoginDisabled() throws MalformedURLException {
        // Given
        customOidcUserService = spy(new CustomOidcUserService(userJdbcService, avatarService, restTemplate, true, true));
        
        // Create existing user with preferred username and password
        userJdbcService.createUser(new User()
            .withUsername(PREFERRED_USERNAME)
            .withDisplayName("Old Display Name")
            .withPassword("old-password")
            .withRole(Role.USER));
        
        OidcUserRequest oidcUserRequest = createOidcUserRequest();
        OidcUser mockOidcUser = createMockOidcUser();
        
        doReturn(mockOidcUser).when(customOidcUserService).getDefaultUser(oidcUserRequest);

        // When
        ExternalUser result = (ExternalUser) customOidcUserService.loadUser(oidcUserRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo(PREFERRED_USERNAME);
        assertThat(result.getDisplayName()).isEqualTo(DISPLAY_NAME);
        assertThat(result.getPassword()).isEmpty(); // Password should be cleared
        
        // Verify user was updated in the database
        Optional<User> updatedUser = userJdbcService.findByUsername(PREFERRED_USERNAME);
        assertThat(updatedUser).isPresent();
        assertThat(updatedUser.get().getDisplayName()).isEqualTo(DISPLAY_NAME);
        assertThat(updatedUser.get().getPassword()).isEmpty(); // Password should be cleared
        assertThat(updatedUser.get().getExternalId()).isEqualTo(EXTERNAL_ID);
    }

    @Test
    void testLoadUser_ExistingUser_LocalLoginEnabled() throws MalformedURLException {
        // Given
        customOidcUserService = spy(new CustomOidcUserService(userJdbcService, avatarService, restTemplate, true, false));
        
        // Create existing user with password
        userJdbcService.createUser(new User()
            .withUsername(PREFERRED_USERNAME)
            .withExternalId(EXTERNAL_ID)
            .withDisplayName("Old Display Name")
            .withPassword("existing-password")
            .withRole(Role.USER));
        
        OidcUserRequest oidcUserRequest = createOidcUserRequest();
        OidcUser mockOidcUser = createMockOidcUser();
        
        doReturn(mockOidcUser).when(customOidcUserService).getDefaultUser(oidcUserRequest);

        // When
        ExternalUser result = (ExternalUser) customOidcUserService.loadUser(oidcUserRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo(PREFERRED_USERNAME);
        assertThat(result.getDisplayName()).isEqualTo(DISPLAY_NAME);
        
        // Verify user was updated in the database but password preserved
        Optional<User> updatedUser = userJdbcService.findByUsername(PREFERRED_USERNAME);
        assertThat(updatedUser).isPresent();
        assertThat(updatedUser.get().getDisplayName()).isEqualTo(DISPLAY_NAME);
        assertThat(updatedUser.get().getPassword()).isEqualTo("existing-password"); // Password should be preserved
        assertThat(updatedUser.get().getProfileUrl()).isEqualTo(PROFILE_URL);
        assertThat(updatedUser.get().getExternalId()).isEqualTo(EXTERNAL_ID);
    }

    @Test
    void testLoadUser_AvatarDownloadFailure() throws MalformedURLException {
        // Given
        customOidcUserService = spy(new CustomOidcUserService(userJdbcService, avatarService, restTemplate, true, false));
        OidcUserRequest oidcUserRequest = createOidcUserRequest();
        OidcUser mockOidcUser = createMockOidcUser();
        
        doReturn(mockOidcUser).when(customOidcUserService).getDefaultUser(oidcUserRequest);
        
        // Mock RestTemplate to throw exception
        when(restTemplate.getForObject(eq(URI.create(AVATAR_URL)), eq(byte[].class)))
            .thenThrow(new RuntimeException("Network error"));

        // When
        ExternalUser result = (ExternalUser) customOidcUserService.loadUser(oidcUserRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo(PREFERRED_USERNAME);
        
        // Verify avatar service was not called due to download failure
        verify(avatarService, never()).updateAvatar(any(), any(), any());
    }

    @Test
    void testLoadUser_NoAvatarUrl() throws MalformedURLException {
        // Given
        customOidcUserService = spy(new CustomOidcUserService(userJdbcService, avatarService, restTemplate, true, false));
        OidcUserRequest oidcUserRequest = createOidcUserRequestWithoutAvatar();
        OidcUser mockOidcUser = createMockOidcUserWithoutAvatar();
        
        doReturn(mockOidcUser).when(customOidcUserService).getDefaultUser(oidcUserRequest);

        // When
        ExternalUser result = (ExternalUser) customOidcUserService.loadUser(oidcUserRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo(PREFERRED_USERNAME);
        
        // Verify no avatar download was attempted
        verify(restTemplate, never()).getForObject(any(URI.class), eq(byte[].class));
        verify(avatarService, never()).updateAvatar(any(), any(), any());
    }

    private OidcUserRequest createOidcUserRequest() throws MalformedURLException {
        OidcUserRequest mockRequest = mock(OidcUserRequest.class);
        OidcIdToken mockIdToken = mock(OidcIdToken.class);
        OAuth2AccessToken mockAccessToken = mock(OAuth2AccessToken.class);

        ClientRegistration mockClientRegistration = mock(ClientRegistration.class);
        ClientRegistration.ProviderDetails mockProviderDetails = mock(ClientRegistration.ProviderDetails.class);
        ClientRegistration.ProviderDetails.UserInfoEndpoint mockUserInfoEndpoint = mock(ClientRegistration.ProviderDetails.UserInfoEndpoint.class);
        
        when(mockRequest.getIdToken()).thenReturn(mockIdToken);
        when(mockRequest.getClientRegistration()).thenReturn(mockClientRegistration);
        when(mockRequest.getAccessToken()).thenReturn(mockAccessToken);

        when(mockIdToken.getPreferredUsername()).thenReturn(PREFERRED_USERNAME);
        when(mockIdToken.getIssuer()).thenReturn(URI.create(ISSUER).toURL());
        when(mockIdToken.getSubject()).thenReturn(SUBJECT);
        
        // Mock ClientRegistration details needed by parent OidcUserService
        when(mockClientRegistration.getProviderDetails()).thenReturn(mockProviderDetails);
        when(mockProviderDetails.getUserInfoEndpoint()).thenReturn(mockUserInfoEndpoint);
        when(mockUserInfoEndpoint.getUri()).thenReturn("https://localhost/userinfo");
        when(mockUserInfoEndpoint.getUserNameAttributeName()).thenReturn("preferred_username");
        when(mockClientRegistration.getClientId()).thenReturn("test-client");
        when(mockClientRegistration.getClientSecret()).thenReturn("test-secret");
        when(mockClientRegistration.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(mockClientRegistration.getClientAuthenticationMethod()).thenReturn(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        
        return mockRequest;
    }

    private OidcUserRequest createOidcUserRequestWithoutAvatar() throws MalformedURLException {
        OidcUserRequest mockRequest = mock(OidcUserRequest.class);
        OidcIdToken mockIdToken = mock(OidcIdToken.class);
        OAuth2AccessToken mockAccessToken = mock(OAuth2AccessToken.class);
        ClientRegistration mockClientRegistration = mock(ClientRegistration.class);
        ClientRegistration.ProviderDetails mockProviderDetails = mock(ClientRegistration.ProviderDetails.class);
        ClientRegistration.ProviderDetails.UserInfoEndpoint mockUserInfoEndpoint = mock(ClientRegistration.ProviderDetails.UserInfoEndpoint.class);
        
        when(mockRequest.getIdToken()).thenReturn(mockIdToken);
        when(mockRequest.getClientRegistration()).thenReturn(mockClientRegistration);
        when(mockRequest.getAccessToken()).thenReturn(mockAccessToken);

        when(mockIdToken.getPreferredUsername()).thenReturn(PREFERRED_USERNAME);
        when(mockIdToken.getIssuer()).thenReturn(URI.create(ISSUER).toURL());
        when(mockIdToken.getSubject()).thenReturn(SUBJECT);
        
        // Mock ClientRegistration details needed by parent OidcUserService
        when(mockClientRegistration.getProviderDetails()).thenReturn(mockProviderDetails);
        when(mockProviderDetails.getUserInfoEndpoint()).thenReturn(mockUserInfoEndpoint);
        when(mockUserInfoEndpoint.getUri()).thenReturn("https://localhost/userinfo");
        when(mockUserInfoEndpoint.getUserNameAttributeName()).thenReturn("preferred_username");
        when(mockClientRegistration.getClientId()).thenReturn("test-client");
        when(mockClientRegistration.getClientSecret()).thenReturn("test-secret");
        when(mockClientRegistration.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(mockClientRegistration.getClientAuthenticationMethod()).thenReturn(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        
        return mockRequest;
    }

    private OidcUser createMockOidcUser() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", SUBJECT);
        claims.put("preferred_username", PREFERRED_USERNAME);
        claims.put("name", DISPLAY_NAME);
        claims.put("given_name", "Test");
        claims.put("family_name", "User");
        claims.put("picture", AVATAR_URL);
        claims.put("profile", PROFILE_URL);
        claims.put("iss", ISSUER);

        OidcIdToken idToken = new OidcIdToken(
            "token-value",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            claims
        );

        return new DefaultOidcUser(null, idToken);
    }

    private OidcUser createMockOidcUserWithoutAvatar() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", SUBJECT);
        claims.put("preferred_username", PREFERRED_USERNAME);
        claims.put("name", DISPLAY_NAME);
        claims.put("given_name", "Test");
        claims.put("family_name", "User");
        claims.put("profile", PROFILE_URL);
        claims.put("iss", ISSUER);

        OidcIdToken idToken = new OidcIdToken(
            "token-value",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            claims
        );

        return new DefaultOidcUser(null, idToken);
    }
}
