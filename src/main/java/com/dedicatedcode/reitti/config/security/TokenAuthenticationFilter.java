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
public class TokenAuthenticationFilter extends BaseTokenAuthenticationFilter {

    public TokenAuthenticationFilter(ApiTokenService apiTokenService) {
        super(apiTokenService);
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
            Optional<ApiToken> tokenOpt = apiTokenService.getToken(authHeader);

            if (tokenOpt.isPresent()) {
                ApiToken token = tokenOpt.get();
                User authenticatedUser = token.getUser().withRole(Role.API_ACCESS);
                Device authenticatedDevice = token.getDevice();

                UserDeviceAuthenticationToken authenticationToken = new UserDeviceAuthenticationToken(
                        authenticatedUser,
                        authenticatedDevice
                );

                trackApiTokenUsage(request, authHeader);
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}
