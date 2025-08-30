package com.dedicatedcode.reitti.config;

import com.dedicatedcode.reitti.service.ApiTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.filter.OncePerRequestFilter;

public abstract class BaseTokenAuthenticationFilter extends OncePerRequestFilter {
    protected final ApiTokenService apiTokenService;

    protected BaseTokenAuthenticationFilter(ApiTokenService apiTokenService) {
        this.apiTokenService = apiTokenService;
    }

    protected void trackApiTokenUsage(HttpServletRequest request, String token) {
        // Extract the path and remote IP of the request, supporting reverse proxy
        String requestPath = request.getRequestURI();
        String remoteIp = getClientIpAddress(request);

        this.apiTokenService.trackUsage(token, requestPath, remoteIp);
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
