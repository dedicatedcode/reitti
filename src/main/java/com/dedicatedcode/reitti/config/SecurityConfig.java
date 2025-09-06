package com.dedicatedcode.reitti.config;

import com.dedicatedcode.reitti.model.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

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

    @Autowired(required = false)
    private LogoutSuccessHandler oidcLogoutSuccessHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/access", "/error").permitAll()
                        .requestMatchers("/settings/**").hasAnyRole(Role.ADMIN.name(), Role.USER.name())
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/img/**", "/error/magic-link/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/v1/reitti-integration/notify/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(magicLinkSessionValidationFilter, AuthorizationFilter.class)
                .addFilterBefore(magicLinkAuthenticationFilter, MagicLinkSessionValidationFilter.class)
                .addFilterBefore(bearerTokenAuthFilter, MagicLinkAuthenticationFilter.class)
                .addFilterBefore(urlTokenAuthenticationFilter, TokenAuthenticationFilter.class)
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(customAuthenticationSuccessHandler)
                )
                .rememberMe(rememberMe -> rememberMe
                        .key("uniqueAndSecretKey")
                        .tokenValiditySeconds(2592000) // 30 days
                        .rememberMeParameter("remember-me")
                        .useSecureCookie(false)
                )
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
