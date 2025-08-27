package com.dedicatedcode.reitti.config;

import com.dedicatedcode.reitti.model.ApiTokenUsage;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.service.ApiTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {
    private final ApiTokenService apiTokenService;

    public TokenAuthenticationFilter(ApiTokenService apiTokenService) {
        this.apiTokenService = apiTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("X-API-Token");
        if (authHeader == null) {
            authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                authHeader = authHeader.substring(7);
            }
        }

        if(authHeader != null) {
            Optional<User> user = apiTokenService.getUserByToken(authHeader);

            if (user.isPresent()) {
                User authenticatedUser = user.get();
                UsernamePasswordAuthenticationToken authenticationToken = 
                    new UsernamePasswordAuthenticationToken(
                        authenticatedUser, 
                        null,
                        authenticatedUser.getAuthorities()
                    );
                
                // Extract the path and remote IP of the request, supporting reverse proxy
                String requestPath = request.getRequestURI();
                String remoteIp = getClientIpAddress(request);
                
                this.apiTokenService.trackUsage(authHeader, requestPath, remoteIp);
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        // Check for X-Forwarded-For header (common in reverse proxy setups)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }
        
        // Check for X-Real-IP header (used by nginx)
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        
        // Check for X-Forwarded header
        String xForwarded = request.getHeader("X-Forwarded");
        if (xForwarded != null && !xForwarded.isEmpty() && !"unknown".equalsIgnoreCase(xForwarded)) {
            return xForwarded;
        }
        
        // Check for Forwarded-For header
        String forwardedFor = request.getHeader("Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(forwardedFor)) {
            return forwardedFor;
        }
        
        // Check for Forwarded header
        String forwarded = request.getHeader("Forwarded");
        if (forwarded != null && !forwarded.isEmpty() && !"unknown".equalsIgnoreCase(forwarded)) {
            return forwarded;
        }
        
        // Fall back to remote address
        return request.getRemoteAddr();
    }
}
