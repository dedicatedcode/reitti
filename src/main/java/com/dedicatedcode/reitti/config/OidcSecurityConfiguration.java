package com.dedicatedcode.reitti.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

@Configuration
@ConditionalOnProperty(name = "reitti.security.oidc.enabled", havingValue = "true")
public class OidcSecurityConfiguration {

  @Bean
  public OAuth2LoginConfigurer<HttpSecurity> oauth2LoginConfigurer(
      CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler,
      CustomOidcUserService customOidcUserService) {
    return new OAuth2LoginConfigurer<HttpSecurity>()
        .loginPage("/login")
        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(customOidcUserService))
        .successHandler(customAuthenticationSuccessHandler);
  }

  @Bean
  public LogoutSuccessHandler oidcLogoutSuccessHandler(ClientRegistrationRepository clientRegistrationRepository) {
    OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler = new OidcClientInitiatedLogoutSuccessHandler(
        clientRegistrationRepository);

    oidcLogoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}/login?logout");

    return oidcLogoutSuccessHandler;
  }
}
