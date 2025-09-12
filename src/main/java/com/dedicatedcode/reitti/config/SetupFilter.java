package com.dedicatedcode.reitti.config;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class SetupFilter implements Filter {

    private final UserJdbcService userService;

    public SetupFilter(UserJdbcService userService) {
        this.userService = userService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String requestURI = httpRequest.getRequestURI();

        // Skip setup check for setup pages, static resources, and health checks
        if (requestURI.startsWith("/setup") ||
                requestURI.startsWith("/css") ||
                requestURI.startsWith("/js") ||
                requestURI.startsWith("/images") ||
                requestURI.startsWith("/img") ||
                requestURI.equals("/actuator/health")) {
            chain.doFilter(request, response);
            return;
        }

        // Check if admin has empty password
        if (hasAdminWithEmptyPassword()) {
            httpResponse.sendRedirect("/setup");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean hasAdminWithEmptyPassword() {
        return userService.getAllUsers().stream()
                .filter(user -> user.getRole() == Role.ADMIN)
                .anyMatch(admin -> {
                    String password = admin.getPassword();
                    return password == null || password.isEmpty();
                });
    }
}