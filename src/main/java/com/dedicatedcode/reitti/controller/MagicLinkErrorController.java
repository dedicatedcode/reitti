package com.dedicatedcode.reitti.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MagicLinkErrorController {

    @GetMapping("/error/magic-link")
    public String magicLinkError(@RequestParam(required = false) String error, Model model) {
        String errorMessage = switch (error) {
            case "invalid" -> "Invalid or expired magic link";
            case "expired" -> "Magic link has expired";
            case "user-not-found" -> "User not found for this magic link";
            case "processing" -> "An error occurred while processing the magic link";
            default -> "An unknown error occurred with the magic link";
        };
        
        model.addAttribute("error", errorMessage);
        return "error/magic-link-error";
    }
}
