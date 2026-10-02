package com.dedicatedcode.reitti.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects login form submissions for usernames that {@link LoginAttemptService} currently blocks. The response is the
 * same as for wrong credentials, so it does not reveal whether a username exists or is blocked.
 * Not a Spring bean on purpose: it is only added to the security filter chain, not to the servlet filters.
 */
public class LoginThrottleFilter extends OncePerRequestFilter {
    private final LoginAttemptService loginAttemptService;

    public LoginThrottleFilter(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        boolean isLoginAttempt = "POST".equals(request.getMethod())
                && (request.getContextPath() + "/login").equals(request.getRequestURI());
        if (isLoginAttempt && loginAttemptService.isBlocked(request.getParameter("username"))) {
            response.sendRedirect(request.getContextPath() + "/login?error");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
