package com.dedicatedcode.reitti.config.security;

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

    /**
     * API tokens are handed to devices and third-party apps (often embedded in URLs), so they only authenticate
     * API calls. The web UI (settings, user management, token management, data deletion) requires a real login.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !(path.startsWith("/api/") || path.equals("/settings/integrations/reitti.properties"));
    }

    protected void trackApiTokenUsage(HttpServletRequest request, String token) {
        String requestPath = request.getRequestURI();
        // the proxy headers are resolved by Tomcat's RemoteIpValve, which only trusts them from internal proxies;
        // reading X-Forwarded-For here directly would let any client choose the logged address
        String remoteIp = request.getRemoteAddr();
        this.apiTokenService.trackUsage(token, requestPath, remoteIp);
    }


    protected final boolean authenticateWithToken(HttpServletRequest request, HttpServletResponse response, String requestedToken) {
        Optional<ApiToken> tokenOpt = apiTokenService.getToken(requestedToken);

        if (tokenOpt.isPresent()) {
            ApiToken token = tokenOpt.get();
            User authenticatedUser = token.getUser();
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
