package com.dedicatedcode.reitti.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class HtmxAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        // Check if the request is coming from HTMX
        if ("true".equals(request.getHeader("HX-Request"))) {
            // Tell HTMX to redirect the whole window to the login page
            response.setHeader("HX-Redirect", "/login");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        } else {
            // Standard behavior for non-HTMX requests (regular 302 redirect)
            response.sendRedirect("/login");
        }
    }
}
