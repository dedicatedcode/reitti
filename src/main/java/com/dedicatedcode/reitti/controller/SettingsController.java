package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.ApiToken;
import com.dedicatedcode.reitti.model.SignificantPlace;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.service.ApiTokenService;
import com.dedicatedcode.reitti.service.PlaceService;
import com.dedicatedcode.reitti.service.QueueStatsService;
import com.dedicatedcode.reitti.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
    private final PlaceService placeService;

    public SettingsController(ApiTokenService apiTokenService, UserService userService, 
                             QueueStatsService queueStatsService, PlaceService placeService) {
        this.apiTokenService = apiTokenService;
        this.userService = userService;
        this.queueStatsService = queueStatsService;
        this.placeService = placeService;
    }

    @GetMapping
    public String settings(Authentication authentication, Model model,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(required = false) String tab) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        
        // Load API tokens
        List<ApiToken> tokens = apiTokenService.getTokensForUser(currentUser);
        model.addAttribute("tokens", tokens);
        
        // Load users (for admin)
        List<User> users = userService.getAllUsers();
        model.addAttribute("users", users);
        
        // Load significant places with pagination (20 per page)
        Page<SignificantPlace> places = placeService.getPlacesForUser(currentUser, PageRequest.of(page, 20));
        model.addAttribute("places", places);
        
        // Load queue stats
        model.addAttribute("queueStats", queueStatsService.getQueueStats());
        
        model.addAttribute("username", authentication.getName());
        
        // Set active tab if provided
        if (tab != null) {
            model.addAttribute("activeTab", tab);
        }
        
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
    
    @PostMapping("/places/{placeId}/update")
    public String updatePlace(@PathVariable Long placeId, 
                             @RequestParam String name,
                             Authentication authentication, 
                             RedirectAttributes redirectAttributes,
                             @RequestParam(defaultValue = "0") int page) {
        try {
            User currentUser = userService.getUserByUsername(authentication.getName());
            placeService.updatePlaceName(placeId, name, currentUser);
            
            redirectAttributes.addFlashAttribute("placeMessage", "Place updated successfully");
            redirectAttributes.addFlashAttribute("placeSuccess", true);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("placeMessage", "Error updating place: " + e.getMessage());
            redirectAttributes.addFlashAttribute("placeSuccess", false);
        }
        
        return "redirect:/settings?tab=places-management&page=" + page;
    }
    
    @PostMapping("/users")
    public String createUser(@RequestParam String username,
                            @RequestParam String displayName,
                            @RequestParam String password,
                            RedirectAttributes redirectAttributes) {
        try {
            userService.createUser(username, displayName, password);
            redirectAttributes.addFlashAttribute("userMessage", "User created successfully");
            redirectAttributes.addFlashAttribute("userSuccess", true);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("userMessage", "Error creating user: " + e.getMessage());
            redirectAttributes.addFlashAttribute("userSuccess", false);
        }
        
        return "redirect:/settings?tab=user-management";
    }
}
