package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.dto.TimelineResponse;
import com.dedicatedcode.reitti.model.ApiToken;
import com.dedicatedcode.reitti.model.GeocodeService;
import com.dedicatedcode.reitti.model.SignificantPlace;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.GeocodeServiceRepository;
import com.dedicatedcode.reitti.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.geolatte.geom.V;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
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
    private final ImportHandler importHandler;
    private final GeocodeServiceRepository geocodeServiceRepository;
    private final int maxErrors;

    public SettingsController(ApiTokenService apiTokenService, UserService userService,
                              QueueStatsService queueStatsService, PlaceService placeService,
                              ImportHandler importHandler,
                              GeocodeServiceRepository geocodeServiceRepository,
                              @Value("${reitti.geocoding.max-errors}") int maxErrors) {
        this.apiTokenService = apiTokenService;
        this.userService = userService;
        this.queueStatsService = queueStatsService;
        this.placeService = placeService;
        this.importHandler = importHandler;
        this.geocodeServiceRepository = geocodeServiceRepository;
        this.maxErrors = maxErrors;
    }

    @GetMapping("/api-tokens-content")
    public String getApiTokensContent(Authentication authentication, Model model) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        model.addAttribute("tokens", apiTokenService.getTokensForUser(currentUser));
        return "fragments/settings :: api-tokens-content";
    }

    @GetMapping("/users-content")
    public String getUsersContent(Authentication authentication, Model model) {
        String currentUsername = authentication.getName();
        List<User> users = userService.getAllUsers();
        model.addAttribute("users", users);
        model.addAttribute("currentUsername", currentUsername);
        return "fragments/settings :: users-content";
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
    public String createToken(Authentication authentication, @RequestParam String name, Model model) {
        User user = userService.getUserByUsername(authentication.getName());

        try {
            ApiToken token = apiTokenService.createToken(user, name);
            model.addAttribute("successMessage", "Token created successfully");
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error creating token: " + e.getMessage());
        }

        // Get updated token list and add to model
        List<ApiToken> tokens = apiTokenService.getTokensForUser(user);
        model.addAttribute("tokens", tokens);

        // Return the api-tokens-content fragment
        return "fragments/settings :: api-tokens-content";
    }

    @PostMapping("/tokens/{tokenId}/delete")
    public String deleteToken(@PathVariable Long tokenId, Authentication authentication, Model model) {
        User user = userService.getUserByUsername(authentication.getName());

        try {
            apiTokenService.deleteToken(tokenId);
            model.addAttribute("successMessage", "Token deleted successfully");
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error deleting token: " + e.getMessage());
        }

        // Get updated token list and add to model
        List<ApiToken> tokens = apiTokenService.getTokensForUser(user);
        model.addAttribute("tokens", tokens);

        // Return the api-tokens-content fragment
        return "fragments/settings :: api-tokens-content";
    }

    @PostMapping("/users/{userId}/delete")
    public String deleteUser(@PathVariable Long userId, Authentication authentication, Model model) {
        String currentUsername = authentication.getName();
        User currentUser = userService.getUserByUsername(currentUsername);

        // Prevent self-deletion
        if (currentUser.getId().equals(userId)) {
            model.addAttribute("errorMessage", "You cannot delete your own account");
        } else {
            try {
                userService.deleteUser(userId);
                model.addAttribute("successMessage", "User deleted successfully");
            } catch (Exception e) {
                model.addAttribute("errorMessage", "Error deleting user: " + e.getMessage());
            }
        }

        // Get updated user list and add to model
        List<User> users = userService.getAllUsers();
        model.addAttribute("users", users);
        model.addAttribute("currentUsername", currentUsername);

        // Return the users-content fragment
        return "fragments/settings :: users-content";
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
    public String createUser(@RequestParam String username,
                             @RequestParam String displayName,
                             @RequestParam String password,
                             Authentication authentication,
                             Model model) {
        String currentUsername = authentication.getName();

        try {
            userService.createUser(username, displayName, password);
            model.addAttribute("successMessage", "User created successfully");
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error creating user: " + e.getMessage());
        }

        // Get updated user list and add to model
        List<User> users = userService.getAllUsers();
        model.addAttribute("users", users);
        model.addAttribute("currentUsername", currentUsername);

        // Return the users-content fragment
        return "fragments/settings :: users-content";
    }

    @PostMapping("/users/update")
    public String updateUser(@RequestParam Long userId,
                             @RequestParam String username,
                             @RequestParam String displayName,
                             @RequestParam(required = false) String password,
                             Authentication authentication,
                             Model model) {
        String currentUsername = authentication.getName();
        User currentUser = userService.getUserById(userId);
        boolean isCurrentUser = currentUser.getId().equals(userId);

        try {
            userService.updateUser(userId, username, displayName, password);
            model.addAttribute("successMessage", "User updated successfully");

            // If the current user was updated, update the authentication
            if (isCurrentUser && !currentUsername.equals(username)) {
                // We need to re-authenticate with the new username
                model.addAttribute("requireRelogin", true);
                model.addAttribute("newUsername", username);
            }
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error updating user: " + e.getMessage());
        }

        // Get updated user list and add to model
        List<User> users = userService.getAllUsers();
        model.addAttribute("users", users);
        model.addAttribute("currentUsername", isCurrentUser ? username : currentUsername);

        // Return the users-content fragment
        return "fragments/settings :: users-content";
    }

    @GetMapping("/queue-stats-content")
    public String getQueueStatsContent(Model model) {
        model.addAttribute("queueStats", queueStatsService.getQueueStats());
        return "fragments/settings :: queue-stats-content";
    }

    @GetMapping("/file-upload-content")
    public String getDataImportContent() {
        return "fragments/settings :: file-upload-content";
    }

    @GetMapping("/integrations-content")
    public String getIntegrationsContent(Authentication authentication, Model model, HttpServletRequest request) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        List<ApiToken> tokens = apiTokenService.getTokensForUser(currentUser);

        // Add the first token if available
        if (!tokens.isEmpty()) {
            model.addAttribute("firstToken", tokens.get(0).getToken());
            model.addAttribute("hasToken", true);
        } else {
            model.addAttribute("hasToken", false);
        }

        // Build the server URL
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();

        StringBuilder serverUrl = new StringBuilder();
        serverUrl.append(scheme).append("://").append(serverName);

        // Only add port if it's not the default port for the scheme
        if ((scheme.equals("http") && serverPort != 80) ||
                (scheme.equals("https") && serverPort != 443)) {
            serverUrl.append(":").append(serverPort);
        }

        model.addAttribute("serverUrl", serverUrl.toString());

        return "fragments/settings :: integrations-content";
    }

    @GetMapping("/user-form")
    public String getUserForm(@RequestParam(required = false) Long userId,
                              @RequestParam(required = false) String username,
                              @RequestParam(required = false) String displayName,
                              Model model) {
        if (userId != null) {
            model.addAttribute("userId", userId);
            model.addAttribute("username", username);
            model.addAttribute("displayName", displayName);
        }
        return "fragments/settings :: user-form";
    }

    @PostMapping("/import/gpx")
    public String importGpx(@RequestParam("file") MultipartFile file,
                            Authentication authentication,
                            Model model) {
        User user = (User) authentication.getPrincipal();

        if (file.isEmpty()) {
            model.addAttribute("uploadErrorMessage", "File is empty");
            return "fragments/settings :: file-upload-content";
        }

        if (!file.getOriginalFilename().endsWith(".gpx")) {
            model.addAttribute("uploadErrorMessage", "Only GPX files are supported");
            return "fragments/settings :: file-upload-content";
        }

        try (InputStream inputStream = file.getInputStream()) {
            Map<String, Object> result = importHandler.importGpx(inputStream, user);

            if ((Boolean) result.get("success")) {
                model.addAttribute("uploadSuccessMessage", result.get("message"));
            } else {
                model.addAttribute("uploadErrorMessage", result.get("error"));
            }

            return "fragments/settings :: file-upload-content";
        } catch (IOException e) {
            model.addAttribute("uploadErrorMessage", "Error processing file: " + e.getMessage());
            return "fragments/settings :: file-upload-content";
        }
    }

    @PostMapping("/import/google-takeout")
    public String importGoogleTakeout(@RequestParam("file") MultipartFile file,
                                      Authentication authentication,
                                      Model model) {
        User user = (User) authentication.getPrincipal();

        if (file.isEmpty()) {
            model.addAttribute("uploadErrorMessage", "File is empty");
            return "fragments/settings :: file-upload-content";
        }

        if (!file.getOriginalFilename().endsWith(".json")) {
            model.addAttribute("uploadErrorMessage", "Only JSON files are supported");
            return "fragments/settings :: file-upload-content";
        }

        try (InputStream inputStream = file.getInputStream()) {
            Map<String, Object> result = importHandler.importGoogleTakeout(inputStream, user);

            if ((Boolean) result.get("success")) {
                model.addAttribute("uploadSuccessMessage", result.get("message"));
            } else {
                model.addAttribute("uploadErrorMessage", result.get("error"));
            }

            return "fragments/settings :: file-upload-content";
        } catch (IOException e) {
            model.addAttribute("uploadErrorMessage", "Error processing file: " + e.getMessage());
            return "fragments/settings :: file-upload-content";
        }
    }

    @GetMapping("/geocode-services-content")
    public String getGeocodeServicesContent(Model model) {
        model.addAttribute("geocodeServices", geocodeServiceRepository.findAllByOrderByNameAsc());
        model.addAttribute("maxErrors", maxErrors);
        return "fragments/settings :: geocode-services-content";
    }

    @PostMapping("/geocode-services")
    public String createGeocodeService(@RequestParam String name,
                                       @RequestParam String urlTemplate,
                                       Model model) {
        try {
            GeocodeService service = new GeocodeService(name, urlTemplate);
            geocodeServiceRepository.save(service);
            model.addAttribute("successMessage", "Geocode service created successfully");
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error creating geocode service: " + e.getMessage());
        }

        model.addAttribute("geocodeServices", geocodeServiceRepository.findAllByOrderByNameAsc());
        model.addAttribute("maxErrors", maxErrors);
        return "fragments/settings :: geocode-services-content";
    }

    @PostMapping("/geocode-services/{id}/toggle")
    public String toggleGeocodeService(@PathVariable Long id, Model model) {
        GeocodeService service = geocodeServiceRepository.findById(id).orElseThrow();
        service.setEnabled(!service.isEnabled());
        if (service.isEnabled()) {
            service.setErrorCount(0);
        }
        geocodeServiceRepository.save(service);
        model.addAttribute("geocodeServices", geocodeServiceRepository.findAllByOrderByNameAsc());
        model.addAttribute("maxErrors", maxErrors);
        return "fragments/settings :: geocode-services-content";
    }

    @PostMapping("/geocode-services/{id}/delete")
    public String deleteGeocodeService(@PathVariable Long id, Model model) {
        GeocodeService service = geocodeServiceRepository.findById(id).orElseThrow();
        geocodeServiceRepository.delete(service);
        model.addAttribute("geocodeServices", geocodeServiceRepository.findAllByOrderByNameAsc());
        model.addAttribute("maxErrors", maxErrors);
        return "fragments/settings :: geocode-services-content";
    }

    @PostMapping("/geocode-services/{id}/reset-errors")
    public String resetGeocodeServiceErrors(@PathVariable Long id, Model model) {
        GeocodeService service = geocodeServiceRepository.findById(id).orElseThrow();
        service.setErrorCount(0);
        service.setEnabled(true);
        geocodeServiceRepository.save(service);
        model.addAttribute("geocodeServices", geocodeServiceRepository.findAllByOrderByNameAsc());
        model.addAttribute("maxErrors", maxErrors);
        return "fragments/settings :: geocode-services-content";
    }
}
