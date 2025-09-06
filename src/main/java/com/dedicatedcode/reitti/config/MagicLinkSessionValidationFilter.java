package com.dedicatedcode.reitti.config;

import com.dedicatedcode.reitti.model.security.MagicLinkToken;
import com.dedicatedcode.reitti.repository.MagicLinkJdbcService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

@Component
public class MagicLinkSessionValidationFilter extends OncePerRequestFilter {
    
    private final MagicLinkJdbcService magicLinkJdbcService;
    
    public MagicLinkSessionValidationFilter(MagicLinkJdbcService magicLinkJdbcService) {
        this.magicLinkJdbcService = magicLinkJdbcService;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth instanceof MagicLinkAuthenticationToken magicLinkAuth) {
            Optional<MagicLinkToken> token = magicLinkJdbcService.findById(magicLinkAuth.getMagicLinkTokenId());
            
            if (token.isEmpty() || (token.get().getExpiryDate() != null && 
                token.get().getExpiryDate().isBefore(Instant.now()))) {
                
                SecurityContextHolder.clearContext();
                request.getSession().invalidate();
                response.sendRedirect("/error/magic-link?error=invalid");
                return;
            }
        }
        
        filterChain.doFilter(request, response);
    }
}
