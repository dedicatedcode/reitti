package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.dto.TimelineResponse;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    // HTMX endpoints for the settings overlay
    @GetMapping("/api-tokens-content")
    public String getApiTokensContent(Authentication authentication, Model model) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        model.addAttribute("tokens", apiTokenService.getTokensForUser(currentUser));
        return "fragments/settings :: api-tokens-content";
    }
    
    // Original JSON endpoint kept for compatibility
    @GetMapping("/api-tokens")
    @ResponseBody
    public List<ApiToken> getApiTokens(Authentication authentication) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        return apiTokenService.getTokensForUser(currentUser);
    }
    
    @GetMapping("/users-content")
    public String getUsersContent(Authentication authentication, Model model) {
        String currentUsername = authentication.getName();
        List<User> users = userService.getAllUsers();
        model.addAttribute("users", users);
        model.addAttribute("currentUsername", currentUsername);
        return "fragments/settings :: users-content";
    }
    
    // Original JSON endpoint kept for compatibility
    @GetMapping("/users")
    @ResponseBody
    public List<Map<String, Object>> getUsers(Authentication authentication) {
        String currentUsername = authentication.getName();
        return userService.getAllUsers().stream()
            .map(user -> {
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("id", user.getId());
                userMap.put("username", user.getUsername());
                userMap.put("displayName", user.getDisplayName());
                userMap.put("currentUser", user.getUsername().equals(currentUsername));
                return userMap;
            })
            .collect(Collectors.toList());
    }
    
    @GetMapping("/places-content")
    public String getPlacesContent(Authentication authentication, 
                                  @RequestParam(defaultValue = "0") int page,
                                  Model model) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        Page<SignificantPlace> placesPage = placeService.getPlacesForUser(currentUser, PageRequest.of(page, 20));
        
        // Convert to PlaceInfo objects
        List<TimelineResponse.PlaceInfo> places = placesPage.getContent().stream()
            .map(place -> new TimelineResponse.PlaceInfo(
                place.getId(),
                place.getName(),
                place.getAddress(),
                place.getCategory(),
                place.getLatitudeCentroid(),
                place.getLongitudeCentroid()
            ))
            .collect(Collectors.toList());
        
        // Add pagination info to model
        model.addAttribute("currentPage", placesPage.getNumber());
        model.addAttribute("totalPages", placesPage.getTotalPages());
        model.addAttribute("places", places);
        model.addAttribute("isEmpty", places.isEmpty());
        
        return "fragments/settings :: places-content";
    }

    @PostMapping("/tokens")
    @ResponseBody
    public Map<String, Object> createToken(Authentication authentication, @RequestParam String name) {
        Map<String, Object> response = new HashMap<>();
        User user = userService.getUserByUsername(authentication.getName());
        
        try {
            ApiToken token = apiTokenService.createToken(user, name);
            response.put("message", "Token created successfully");
            response.put("success", true);
            response.put("token", token);
        } catch (Exception e) {
            response.put("message", "Error creating token: " + e.getMessage());
            response.put("success", false);
        }
        
        return response;
    }
    
    @PostMapping("/tokens/{tokenId}/delete")
    @ResponseBody
    public Map<String, Object> deleteToken(@PathVariable Long tokenId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            apiTokenService.deleteToken(tokenId);
            response.put("message", "Token deleted successfully");
            response.put("success", true);
        } catch (Exception e) {
            response.put("message", "Error deleting token: " + e.getMessage());
            response.put("success", false);
        }
        
        return response;
    }
    
    @PostMapping("/users/{userId}/delete")
    @ResponseBody
    public Map<String, Object> deleteUser(@PathVariable Long userId, Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Prevent self-deletion
            User currentUser = userService.getUserByUsername(authentication.getName());
            if (currentUser.getId().equals(userId)) {
                response.put("message", "You cannot delete your own account");
                response.put("success", false);
                return response;
            }
            
            userService.deleteUser(userId);
            response.put("message", "User deleted successfully");
            response.put("success", true);
        } catch (Exception e) {
            response.put("message", "Error deleting user: " + e.getMessage());
            response.put("success", false);
        }
        
        return response;
    }
    
    @PostMapping("/places/{placeId}/update")
    @ResponseBody
    public Map<String, Object> updatePlace(@PathVariable Long placeId, 
                                         @RequestParam String name,
                                         Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            User currentUser = userService.getUserByUsername(authentication.getName());
            placeService.updatePlaceName(placeId, name, currentUser);
            
            response.put("message", "Place updated successfully");
            response.put("success", true);
        } catch (Exception e) {
            response.put("message", "Error updating place: " + e.getMessage());
            response.put("success", false);
        }
        
        return response;
    }
    
    @PostMapping("/users")
    @ResponseBody
    public Map<String, Object> createUser(@RequestParam String username,
                                        @RequestParam String displayName,
                                        @RequestParam String password) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            userService.createUser(username, displayName, password);
            response.put("message", "User created successfully");
            response.put("success", true);
        } catch (Exception e) {
            response.put("message", "Error creating user: " + e.getMessage());
            response.put("success", false);
        }
        
        return response;
    }
    @GetMapping("/queue-stats-content")
    public String getQueueStatsContent(Model model) {
        model.addAttribute("queueStats", queueStatsService.getQueueStats());
        return "fragments/settings :: queue-stats-content";
    }
}
