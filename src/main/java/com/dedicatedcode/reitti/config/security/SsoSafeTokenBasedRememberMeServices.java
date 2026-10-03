package com.dedicatedcode.reitti.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.InvalidCookieException;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SsoSafeTokenBasedRememberMeServices extends TokenBasedRememberMeServices {

    public SsoSafeTokenBasedRememberMeServices(UserDetailsService userDetailsService,
                                               RememberMeKeyProvider rememberMeKeyProvider) {
        super(rememberMeKeyProvider.getKey(), userDetailsService);
    }

    @Override
    protected UserDetails processAutoLoginCookie(String[] cookieTokens, HttpServletRequest request,
                                                 HttpServletResponse response) {
        UserDetails user = super.processAutoLoginCookie(cookieTokens, request, response);
        if (!StringUtils.hasLength(user.getPassword())) {
            throw new InvalidCookieException("remember-me is not available to accounts without a local password");
        }
        return user;
    }
}