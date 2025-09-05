package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.security.MagicLinkToken;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.MagicLinkJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
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
    private final UserJdbcService userJdbcService;

    public MagicLinkAccessController(MagicLinkJdbcService magicLinkJdbcService, UserJdbcService userJdbcService) {
        this.magicLinkJdbcService = magicLinkJdbcService;
        this.userJdbcService = userJdbcService;
    }

    @GetMapping("/access")
    public String accessWithToken(@RequestParam(name = "mt") String token, Model model) {
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
        Optional<User> user = magicLinkJdbcService.findUserIdByToken(linkToken.getId()).flatMap(userJdbcService::findById);

        if (user.isEmpty()) {
            model.addAttribute("error", "User not found for this magic link");
            return "error/magic-link-error";
        }

        // Update last used timestamp
        magicLinkJdbcService.updateLastUsed(linkToken.getId());

        String specialRole = "ROLE_MAGIC_LINK_" + linkToken.getAccessLevel().name();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user.get(),
                null,
                Collections.singletonList(new SimpleGrantedAuthority(specialRole))
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        return "redirect:/";

    }
}
