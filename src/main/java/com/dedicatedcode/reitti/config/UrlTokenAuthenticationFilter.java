package com.dedicatedcode.reitti.config;

import com.dedicatedcode.reitti.model.Role;
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
        String token = request.getParameter("token");

        if (token != null && !token.isEmpty()) {
            Optional<User> user = apiTokenService.getUserByToken(token);

            if (user.isPresent()) {
                User authenticatedUser = user.get().withRole(Role.API_ACCESS);
                UsernamePasswordAuthenticationToken authenticationToken = 
                    new UsernamePasswordAuthenticationToken(
                        authenticatedUser, 
                        null,
                        authenticatedUser.getAuthorities()
                    );
                trackApiTokenUsage(request, token);
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
