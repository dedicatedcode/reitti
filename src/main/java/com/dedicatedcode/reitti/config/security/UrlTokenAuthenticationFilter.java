package com.dedicatedcode.reitti.config.security;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.ApiToken;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.ApiTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Component
public class UrlTokenAuthenticationFilter extends BaseTokenAuthenticationFilter {

    public UrlTokenAuthenticationFilter(ApiTokenService apiTokenService) {
        super(apiTokenService);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // Extract token from URL parameter
        String requestedToken = request.getParameter("token");

        if (requestedToken != null && !requestedToken.isEmpty()) {
            if (authenticateWithToken(request, response, requestedToken)) {
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

}
