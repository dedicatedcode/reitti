package com.dedicatedcode.reitti.config.security;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.ApiToken;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.ApiTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.Optional;

public abstract class BaseTokenAuthenticationFilter extends OncePerRequestFilter {
    protected final ApiTokenService apiTokenService;

    protected BaseTokenAuthenticationFilter(ApiTokenService apiTokenService) {
        this.apiTokenService = apiTokenService;
    }

    protected void trackApiTokenUsage(HttpServletRequest request, String token) {
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


    protected final boolean authenticateWithToken(HttpServletRequest request, HttpServletResponse response, String requestedToken) {
        Optional<ApiToken> tokenOpt = apiTokenService.getToken(requestedToken);

        if (tokenOpt.isPresent()) {
            ApiToken token = tokenOpt.get();
            User authenticatedUser = token.getUser().withRole(Role.API_ACCESS);
            Device authenticatedDevice = token.getDevice();

            UserDeviceAuthenticationToken authenticationToken = new UserDeviceAuthenticationToken(
                    authenticatedUser,
                    authenticatedDevice
            );

            trackApiTokenUsage(request, requestedToken);
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return true;
        }
        return false;
    }
}
