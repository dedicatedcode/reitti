package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.ApiToken;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.service.ApiTokenService;
import com.dedicatedcode.reitti.service.QueueStatsService;
import com.dedicatedcode.reitti.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/settings")
public class SettingsController {

    private final ApiTokenService apiTokenService;
    private final UserService userService;
    private final QueueStatsService queueStatsService;

    public SettingsController(ApiTokenService apiTokenService, UserService userService, QueueStatsService queueStatsService) {
        this.apiTokenService = apiTokenService;
        this.userService = userService;
        this.queueStatsService = queueStatsService;
    }

    @GetMapping
    public String settings(Authentication authentication, Model model) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        
        // Load API tokens
        List<ApiToken> tokens = apiTokenService.getTokensForUser(currentUser);
        model.addAttribute("tokens", tokens);
        
        // Load users (for admin)
        List<User> users = userService.getAllUsers();
        model.addAttribute("users", users);
        
        // Load queue stats
        Map<String, Object> queueStats = queueStatsService.getQueueStats();
        model.addAttribute("queueStats", queueStats);
        
        model.addAttribute("username", authentication.getName());
        
        return "settings";
    }
    
    @PostMapping("/tokens")
    public String createToken(Authentication authentication, @RequestParam String name, RedirectAttributes redirectAttributes) {
        User user = userService.getUserByUsername(authentication.getName());
        
        try {
            ApiToken token = apiTokenService.createToken(user, name);
            redirectAttributes.addFlashAttribute("tokenMessage", "Token created successfully");
            redirectAttributes.addFlashAttribute("tokenSuccess", true);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("tokenMessage", "Error creating token: " + e.getMessage());
            redirectAttributes.addFlashAttribute("tokenSuccess", false);
        }
        
        return "redirect:/settings";
    }
    
    @PostMapping("/tokens/{tokenId}/delete")
    public String deleteToken(@PathVariable Long tokenId, RedirectAttributes redirectAttributes) {
        try {
            apiTokenService.deleteToken(tokenId);
            redirectAttributes.addFlashAttribute("tokenMessage", "Token deleted successfully");
            redirectAttributes.addFlashAttribute("tokenSuccess", true);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("tokenMessage", "Error deleting token: " + e.getMessage());
            redirectAttributes.addFlashAttribute("tokenSuccess", false);
        }
        
        return "redirect:/settings";
    }
    
    @PostMapping("/users/{userId}/delete")
    public String deleteUser(@PathVariable Long userId, Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            // Prevent self-deletion
            User currentUser = userService.getUserByUsername(authentication.getName());
            if (currentUser.getId().equals(userId)) {
                redirectAttributes.addFlashAttribute("userMessage", "You cannot delete your own account");
                redirectAttributes.addFlashAttribute("userSuccess", false);
                return "redirect:/settings";
            }
            
            userService.deleteUser(userId);
            redirectAttributes.addFlashAttribute("userMessage", "User deleted successfully");
            redirectAttributes.addFlashAttribute("userSuccess", true);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("userMessage", "Error deleting user: " + e.getMessage());
            redirectAttributes.addFlashAttribute("userSuccess", false);
        }
        
        return "redirect:/settings";
    }
}
