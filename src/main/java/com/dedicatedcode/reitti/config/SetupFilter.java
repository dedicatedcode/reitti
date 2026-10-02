package com.dedicatedcode.reitti.config;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.ContextPathHolder;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class SetupFilter implements Filter {

    private final UserJdbcService userService;
    private final ContextPathHolder contextPathHolder;
    private final boolean localLoginDisabled;

    public SetupFilter(UserJdbcService userService, ContextPathHolder contextPathHolder,
                       @Value("${reitti.security.local-login.disable:false}") boolean localLoginDisabled) {
        this.userService = userService;
        this.contextPathHolder = contextPathHolder;
        this.localLoginDisabled = localLoginDisabled;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (localLoginDisabled) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String requestURI = httpRequest.getRequestURI();

        // Skip setup check for setup pages, static resources, and health checks
        if (requestURI.contains("/setup") ||
                requestURI.contains("/css") ||
                requestURI.contains("/js") ||
                requestURI.contains("/images") ||
                requestURI.contains("/img") ||
                requestURI.contains("/actuator/health")) {
            chain.doFilter(request, response);
            return;
        }

        // Check if admin has empty password
        if (hasAdminWithEmptyPassword()) {
            httpResponse.sendRedirect(contextPathHolder.getContextPath() + "/setup");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean hasAdminWithEmptyPassword() {
        return userService.getAllUsers().stream().anyMatch(SetupFilter::isUnconfiguredLocalAdmin);
    }

    /**
     * The bootstrap admin created by the migrations has an empty password until the setup page sets one.
     * Users created via OIDC also have an empty password, so they must never be treated as such an admin,
     * otherwise anyone could set a password for them through the unauthenticated setup page.
     */
    public static boolean isUnconfiguredLocalAdmin(User user) {
        String password = user.getPassword();
        return user.getRole() == Role.ADMIN
                && (password == null || password.isEmpty())
                && (user.getExternalId() == null || user.getExternalId().isEmpty());
    }
}