package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.security.MagicLinkToken;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.MagicLinkJdbcService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

@Controller
public class MagicLinkAccessController {

    private final MagicLinkJdbcService magicLinkJdbcService;

    public MagicLinkAccessController(MagicLinkJdbcService magicLinkJdbcService) {
        this.magicLinkJdbcService = magicLinkJdbcService;
    }

    @GetMapping("/access")
    public String accessWithToken(@RequestParam String token, Model model) {
        try {
            // Find the token
            Optional<MagicLinkToken> magicLinkToken = magicLinkJdbcService.findByRawToken(token);
            
            if (magicLinkToken.isEmpty()) {
                model.addAttribute("error", "Invalid or expired magic link");
                return "error/magic-link-error";
            }

            MagicLinkToken linkToken = magicLinkToken.get();

            // Check if token is expired
            if (linkToken.getExpiryDate() != null && linkToken.getExpiryDate().isBefore(Instant.now())) {
                model.addAttribute("error", "Magic link has expired");
                return "error/magic-link-error";
            }

            // Find the user associated with this token
            Optional<User> user = magicLinkJdbcService.findUserByTokenId(linkToken.getId());
            
            if (user.isEmpty()) {
                model.addAttribute("error", "User not found for this magic link");
                return "error/magic-link-error";
            }

            // Update last used timestamp
            magicLinkJdbcService.updateLastUsed(linkToken.getId());

            // Create a special authentication with magic link role
            String specialRole = "ROLE_MAGIC_LINK_" + linkToken.getAccessLevel().name();
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user.get(),
                null,
                Collections.singletonList(new SimpleGrantedAuthority(specialRole))
            );

            // Set the authentication in the security context
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Redirect to the main application
            return "redirect:/";

        } catch (Exception e) {
            model.addAttribute("error", "An error occurred while processing the magic link: " + e.getMessage());
            return "error/magic-link-error";
        }
    }
}
