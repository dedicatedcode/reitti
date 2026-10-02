package com.dedicatedcode.reitti.config;

import com.dedicatedcode.reitti.config.security.*;
import com.dedicatedcode.reitti.model.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] USER_ROLES = {Role.ADMIN.name(), Role.USER.name()};
    private static final String[] MAGIC_LINK_FULL = {"MAGIC_LINK_FULL_ACCESS"};
    private static final String[] MAGIC_LINK_LIVE = {"MAGIC_LINK_ONLY_LIVE", "MAGIC_LINK_ONLY_LIVE_WITH_PHOTOS"};
    private static final String[] MAGIC_LINK_LAST_LOCATION = {"MAGIC_LINK_ONLY_LAST_LOCATION"};
    private static final String[] MAGIC_LINK_MEMORY = {"MAGIC_LINK_MEMORY_VIEW_ONLY", "MAGIC_LINK_MEMORY_EDIT_ACCESS"};
    private static final String[] MAGIC_LINK_ROLES = concat(MAGIC_LINK_FULL, MAGIC_LINK_LIVE, MAGIC_LINK_LAST_LOCATION, MAGIC_LINK_MEMORY);

    private static String[] concat(String[]... groups) {
        return Arrays.stream(groups).flatMap(Arrays::stream).toArray(String[]::new);
    }

    @Autowired
    private TokenAuthenticationFilter bearerTokenAuthFilter;

    @Autowired
    private UrlTokenAuthenticationFilter urlTokenAuthenticationFilter;

    @Autowired
    private MagicLinkAuthenticationFilter magicLinkAuthenticationFilter;

    @Autowired
    private MagicLinkSessionValidationFilter magicLinkSessionValidationFilter;

    @Autowired
    private CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;

    @Autowired
    private SetupFilter setupFilter;

    @Autowired
    private HtmxAuthenticationEntryPoint authenticationEntryPoint;

    @Autowired(required = false)
    private LogoutSuccessHandler oidcLogoutSuccessHandler;

    @Autowired
    private RememberMeKeyProvider rememberMeKeyProvider;

    @Autowired
    private UserDetailsService userDetailsService;

    @Bean
    public TokenBasedRememberMeServices rememberMeServices() {
        // The key must be a per-installation secret, see RememberMeKeyProvider.
        TokenBasedRememberMeServices services = new TokenBasedRememberMeServices(rememberMeKeyProvider.getKey(), userDetailsService);
        services.setTokenValiditySeconds(2592000); // 30 days
        services.setParameter("remember-me");
        // Secure flag follows the request (X-Forwarded-Proto behind a proxy); SameSite=Lax blocks cross-site form posts
        services.setCookieCustomizer(cookie -> cookie.setAttribute("SameSite", "Lax"));
        return services;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Deny by default: everything not listed needs a real account (session or API token). Magic-link
                // sessions only get read access to what their view needs; the controllers additionally enforce the
                // owner and the time range a link grants (today only for live links, the memory's range for memory links).
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/access", "/error", "/error/**").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/fonts/**", "/img/**", "/setup", "/setup/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/v1/reitti-integration/notify/**").permitAll()

                        .requestMatchers("/settings/logging", "/settings/logging/**").hasRole(Role.ADMIN.name())
                        .requestMatchers("/settings/**").hasAnyRole(USER_ROLES)

                        // shell of the map views, shared by every kind of session
                        .requestMatchers(HttpMethod.GET, "/", "/manifest.json", "/avatars/**", "/user-css/**", "/api/v1/tiles/**")
                            .hasAnyRole(concat(USER_ROLES, MAGIC_LINK_ROLES))
                        .requestMatchers(HttpMethod.GET, "/timeline/content/range", "/timeline/user-selection", "/events", "/api/v2/locations/metadata/**")
                            .hasAnyRole(concat(USER_ROLES, MAGIC_LINK_FULL, MAGIC_LINK_LIVE, MAGIC_LINK_LAST_LOCATION, MAGIC_LINK_MEMORY))
                        .requestMatchers(HttpMethod.GET, "/api/v2/locations/stream/**")
                            .hasAnyRole(concat(USER_ROLES, MAGIC_LINK_FULL, MAGIC_LINK_LIVE, MAGIC_LINK_MEMORY))
                        .requestMatchers(HttpMethod.GET, "/api/v2/trips/**", "/api/v2/coverage/cells/**")
                            .hasAnyRole(concat(USER_ROLES, MAGIC_LINK_FULL, MAGIC_LINK_LIVE))
                        .requestMatchers(HttpMethod.GET, "/api/v1/visits/**", "/api/v1/raw-location-points/**", "/api/v1/latest-location")
                            .hasAnyRole(concat(USER_ROLES, MAGIC_LINK_FULL))
                        .requestMatchers(HttpMethod.GET, "/api/v1/photos/**")
                            .hasAnyRole(concat(USER_ROLES, MAGIC_LINK_FULL, new String[]{"MAGIC_LINK_ONLY_LIVE_WITH_PHOTOS"}, MAGIC_LINK_MEMORY))
                        .requestMatchers(HttpMethod.GET, "/panoramax/**", "/api/v1/panoramax/**")
                            .hasAnyRole(concat(USER_ROLES, MAGIC_LINK_FULL, MAGIC_LINK_MEMORY))

                        // memories: the list views are for the owner only, a memory link reaches its single memory
                        .requestMatchers("/memories", "/memories/all", "/memories/year/**", "/memories/years-navigation", "/memories/new").hasAnyRole(USER_ROLES)
                        .requestMatchers(HttpMethod.GET, "/memories/*/**", "/api/v2/memories/**")
                            .hasAnyRole(concat(USER_ROLES, MAGIC_LINK_MEMORY))
                        .requestMatchers("/memories/*/**").hasAnyRole(concat(USER_ROLES, new String[]{"MAGIC_LINK_MEMORY_EDIT_ACCESS"}))

                        .anyRequest().hasAnyRole(USER_ROLES)
                )
                .addFilterBefore(magicLinkSessionValidationFilter, AuthorizationFilter.class)
                .addFilterBefore(magicLinkAuthenticationFilter, MagicLinkSessionValidationFilter.class)
                .addFilterBefore(bearerTokenAuthFilter, MagicLinkAuthenticationFilter.class)
                .addFilterBefore(urlTokenAuthenticationFilter, TokenAuthenticationFilter.class)
                .addFilterBefore(setupFilter, MagicLinkSessionValidationFilter.class)
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(customAuthenticationSuccessHandler)
                )
                .rememberMe(rememberMe -> rememberMe
                        .key(rememberMeKeyProvider.getKey())
                        .rememberMeServices(rememberMeServices())
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling.authenticationEntryPoint(authenticationEntryPoint))
                .logout(logout -> {
                    if (oidcLogoutSuccessHandler != null) {
                        logout.logoutSuccessHandler(oidcLogoutSuccessHandler);
                    }
                    logout.deleteCookies("JSESSIONID", "remember-me")
                          .permitAll();
                });

        // Apply OAuth2 configuration if OIDC is enabled
        if (oidcLogoutSuccessHandler != null) {
            http.oauth2Login(oauth2 -> oauth2
                    .loginPage("/login")
                    .successHandler(customAuthenticationSuccessHandler)
            )
            .oauth2Client(Customizer.withDefaults())
            .oidcLogout((logout) -> logout.backChannel(Customizer.withDefaults()));
        }

        return http.build();
    }
}
