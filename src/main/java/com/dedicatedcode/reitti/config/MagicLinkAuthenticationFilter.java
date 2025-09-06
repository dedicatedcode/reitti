package com.dedicatedcode.reitti.config;

import com.dedicatedcode.reitti.model.security.MagicLinkToken;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.MagicLinkJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

@Component
public class MagicLinkAuthenticationFilter extends OncePerRequestFilter {

    private final MagicLinkJdbcService magicLinkJdbcService;
    private final UserJdbcService userJdbcService;

    public MagicLinkAuthenticationFilter(MagicLinkJdbcService magicLinkJdbcService, UserJdbcService userJdbcService) {
        this.magicLinkJdbcService = magicLinkJdbcService;
        this.userJdbcService = userJdbcService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!"/access".equals(request.getRequestURI()) || request.getParameter("mt") == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = request.getParameter("mt");

        try {
            Optional<MagicLinkToken> magicLinkToken = magicLinkJdbcService.findByRawToken(token);

            if (magicLinkToken.isEmpty()) {
                response.sendRedirect("/error/magic-link?error=invalid");
                return;
            }

            MagicLinkToken linkToken = magicLinkToken.get();

            if (linkToken.getExpiryDate() != null && linkToken.getExpiryDate().isBefore(Instant.now())) {
                response.sendRedirect("/error/magic-link?error=expired");
                return;
            }

            Optional<User> user = magicLinkJdbcService.findUserIdByToken(linkToken.getId()).flatMap(userJdbcService::findById);

            if (user.isEmpty()) {
                response.sendRedirect("/error/magic-link?error=user-not-found");
                return;
            }

            magicLinkJdbcService.updateLastUsed(linkToken.getId());

            String specialRole = "ROLE_MAGIC_LINK_" + linkToken.getAccessLevel().name();
            MagicLinkAuthenticationToken authentication = new MagicLinkAuthenticationToken(
                    user.get(),
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority(specialRole)),
                    linkToken.getId()
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            request.getSession().setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    SecurityContextHolder.getContext());

            response.sendRedirect("/");
            return;
        } catch (Exception e) {
            response.sendRedirect("/error/magic-link?error=processing");
        }

        filterChain.doFilter(request, response);
    }
}
