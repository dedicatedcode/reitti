package com.dedicatedcode.reitti.config;

import com.dedicatedcode.reitti.model.security.MagicLinkResourceType;
import com.dedicatedcode.reitti.model.security.MagicLinkToken;
import com.dedicatedcode.reitti.model.security.TokenUser;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.MagicLinkJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.ContextPathHolder;
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
import java.util.List;
import java.util.Optional;

@Component
public class MagicLinkAuthenticationFilter extends OncePerRequestFilter {

    private final MagicLinkJdbcService magicLinkJdbcService;
    private final UserJdbcService userJdbcService;
    private final ContextPathHolder contextPathHolder;

    public MagicLinkAuthenticationFilter(MagicLinkJdbcService magicLinkJdbcService, UserJdbcService userJdbcService, ContextPathHolder contextPathHolder) {
        this.magicLinkJdbcService = magicLinkJdbcService;
        this.userJdbcService = userJdbcService;
        this.contextPathHolder = contextPathHolder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (request.getParameter("mt") == null) {
            filterChain.doFilter(request, response);
            return;
        }
        boolean isMemoryRequest = request.getRequestURI().startsWith(contextPathHolder.getContextPath() + "/memories/");
        if (!((contextPathHolder.getContextPath() + "/access").equals(request.getRequestURI()) || isMemoryRequest)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = request.getParameter("mt");

        try {
            Optional<MagicLinkToken> magicLinkToken = magicLinkJdbcService.findByRawToken(token);

            if (magicLinkToken.isEmpty()) {
                response.sendRedirect(contextPathHolder.getContextPath() + "/error/magic-link?error=invalid");
                return;
            }

            MagicLinkToken linkToken = magicLinkToken.get();

            if (linkToken.getExpiryDate() != null && linkToken.getExpiryDate().isBefore(Instant.now())) {
                response.sendRedirect(contextPathHolder.getContextPath() + "/error/magic-link?error=expired");
                return;
            }
            Long resourceId = null;
            if (isMemoryRequest) {
                try {
                    resourceId = Long.parseLong(request.getRequestURI().substring((contextPathHolder.getContextPath() + "/memories/").length()));
                } catch (NumberFormatException e) {
                    //ignored
                }
                if (linkToken.getResourceType() != MagicLinkResourceType.MEMORY || resourceId == null || resourceId.longValue() != linkToken.getResourceId()) {
                    response.sendRedirect(contextPathHolder.getContextPath() + "/error/magic-link?error=invalid");
                    return;
                }
            }

            Optional<User> user = magicLinkJdbcService.findUserIdByToken(linkToken.getId()).flatMap(userJdbcService::findById);

            if (user.isEmpty()) {
                response.sendRedirect(contextPathHolder.getContextPath() + "/error/magic-link?error=user-not-found");
                return;
            }

            magicLinkJdbcService.updateLastUsed(linkToken.getId());

            String specialRole = "ROLE_MAGIC_LINK_" + linkToken.getAccessLevel().name();
            MagicLinkAuthenticationToken authentication = new MagicLinkAuthenticationToken(
                    new TokenUser(user.get(), linkToken.getResourceType(), resourceId, List.of(specialRole)),
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority(specialRole)),
                    linkToken.getId()
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            request.getSession().setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    SecurityContextHolder.getContext());

            if (linkToken.getResourceType() != MagicLinkResourceType.MEMORY) {
                response.sendRedirect(contextPathHolder.getContextPath() + "/");
            } else {
                response.sendRedirect(contextPathHolder.getContextPath() + "/memories/" + linkToken.getResourceId());
            }
            return;
        } catch (Exception e) {
            response.sendRedirect(contextPathHolder.getContextPath() + "/error/magic-link?error=processing");
        }

        filterChain.doFilter(request, response);
    }
}
