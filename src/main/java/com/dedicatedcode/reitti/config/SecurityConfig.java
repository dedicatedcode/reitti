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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;


@Configuration
@EnableWebSecurity
public class SecurityConfig {

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
    private LoginAttemptService loginAttemptService;

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
        TokenBasedRememberMeServices services = new SsoSafeTokenBasedRememberMeServices(userDetailsService, rememberMeKeyProvider);
        services.setTokenValiditySeconds(2592000); // 30 days
        services.setParameter("remember-me");
        services.setCookieCustomizer(cookie -> cookie.setAttribute("SameSite", "Lax"));
        return services;
    }

    private static RequestMatcher tokenAuthenticatedRequest() {
        return request -> {
            String apiToken = request.getHeader("X-API-Token");
            if (apiToken != null && !apiToken.isBlank()) {
                return true;
            }
            String authHeader = request.getHeader("Authorization");
            return authHeader != null && !authHeader.isBlank();
        };
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CsrfTokenRepository csrfTokenRepository) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/access", "/error").permitAll()
                        .requestMatchers("/settings/logging", "/settings/logging/**").hasRole(Role.ADMIN.name())
                        .requestMatchers("/settings/geocode-services", "/settings/geocode-services/**").hasRole(Role.ADMIN.name())
                        .requestMatchers("/settings/**").hasAnyRole(Role.ADMIN.name(), Role.USER.name())
                        .requestMatchers("/api/v1/photos/**").hasAnyRole(Role.ADMIN.name(),
                                Role.USER.name(),
                                "MAGIC_LINK_FULL_ACCESS",
                                "MAGIC_LINK_ONLY_LIVE_WITH_PHOTOS",
                                "MAGIC_LINK_MEMORY_VIEW_ONLY",
                                "MAGIC_LINK_MEMORY_EDIT_ACCESS")

                        .requestMatchers("/memories/all",
                                         "/memories/year/**",
                                         "/memories/years-navigation",
                                         "/memories/new",
                                         "/memories/*/edit",
                                         "/memories/*/share",
                                         "/memories/*/share/**",
                                         "/memories/*/recalculate")
                                        .hasAnyRole(Role.ADMIN.name(), Role.USER.name())
                        .requestMatchers(HttpMethod.POST,
                                         "/memories",
                                         "/memories/*")
                                        .hasAnyRole(Role.ADMIN.name(), Role.USER.name())
                        .requestMatchers(HttpMethod.DELETE,
                                         "/memories/*")
                                        .hasAnyRole(Role.ADMIN.name(), Role.USER.name())
                        .requestMatchers("/memories/{id}",
                                         "/memories/fragments/empty")
                                        .hasAnyRole(Role.ADMIN.name(),
                                                    Role.USER.name(),
                                                    "MAGIC_LINK_MEMORY_VIEW_ONLY",
                                                    "MAGIC_LINK_MEMORY_EDIT_ACCESS")
                        .requestMatchers("/memories/*/blocks/**")
                                        .hasAnyRole(Role.ADMIN.name(),
                                                    Role.USER.name(),
                                                    "MAGIC_LINK_MEMORY_EDIT_ACCESS")

                        .requestMatchers("/api/v2/locations/stream/**").hasAnyRole(Role.ADMIN.name(), Role.USER.name(),
                                                                                   "MAGIC_LINK_FULL_ACCESS",
                                                                                   "MAGIC_LINK_MEMORY_VIEW_ONLY",
                                                                                   "MAGIC_LINK_MEMORY_EDIT_ACCESS")
                        .requestMatchers("/api/v2/locations/metadata/**").hasAnyRole(Role.ADMIN.name(), Role.USER.name(),
                                                                                     "MAGIC_LINK_FULL_ACCESS",
                                                                                     "MAGIC_LINK_ONLY_LAST_LOCATION",
                                                                                     "MAGIC_LINK_MEMORY_VIEW_ONLY",
                                                                                     "MAGIC_LINK_MEMORY_EDIT_ACCESS")
                        .requestMatchers("/api/v1/visits/**").hasAnyRole(Role.ADMIN.name(), Role.USER.name(), "MAGIC_LINK_FULL_ACCESS",
                                                                         "MAGIC_LINK_MEMORY_VIEW_ONLY",
                                                                         "MAGIC_LINK_MEMORY_EDIT_ACCESS")
                        .requestMatchers("/panoramax/**", "/api/v1/panoramax/**").hasAnyRole(Role.ADMIN.name(),
                                Role.USER.name(),
                                "MAGIC_LINK_FULL_ACCESS",
                                "MAGIC_LINK_MEMORY_VIEW_ONLY",
                                "MAGIC_LINK_MEMORY_EDIT_ACCESS")
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/fonts/**", "/img/**", "/error/magic-link/**", "/setup/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/v1/reitti-integration/notify/**").permitAll()
                        .requestMatchers("/api/v1/tiles/**").authenticated()
                        .anyRequest().hasAnyRole(Role.ADMIN.name(), Role.USER.name())
                )
                .addFilterBefore(magicLinkSessionValidationFilter, AuthorizationFilter.class)
                .addFilterBefore(magicLinkAuthenticationFilter, MagicLinkSessionValidationFilter.class)
                .addFilterBefore(bearerTokenAuthFilter, MagicLinkAuthenticationFilter.class)
                .addFilterBefore(urlTokenAuthenticationFilter, TokenAuthenticationFilter.class)
                .addFilterBefore(setupFilter, MagicLinkSessionValidationFilter.class)
                .addFilterBefore(new LoginThrottleFilter(loginAttemptService), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new CsrfCookieFilter(csrfTokenRepository), CsrfFilter.class)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers(tokenAuthenticatedRequest(), PathPatternRequestMatcher.withDefaults().matcher("/api/v1/reitti-integration/notify/**")))
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
                    logout.deleteCookies("JSESSIONID", "remember-me", "XSRF-TOKEN")
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
