package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.dto.TimelineResponse;
import com.dedicatedcode.reitti.model.*;
import com.dedicatedcode.reitti.repository.GeocodeServiceRepository;
import com.dedicatedcode.reitti.service.*;
import com.dedicatedcode.reitti.service.processing.RawLocationPointProcessingTrigger;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
    private final RawLocationPointProcessingTrigger rawLocationPointProcessingTrigger;
    private final int maxErrors;
    private final boolean dataManagementEnabled;

    @Autowired
    private MessageSource messageSource;

    public SettingsController(ApiTokenService apiTokenService, UserService userService,
                              QueueStatsService queueStatsService, PlaceService placeService,
                              ImportHandler importHandler,
                              GeocodeServiceRepository geocodeServiceRepository,
                              RawLocationPointProcessingTrigger rawLocationPointProcessingTrigger,
                              @Value("${reitti.geocoding.max-errors}") int maxErrors,
                              @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled) {
        this.apiTokenService = apiTokenService;
        this.userService = userService;
        this.queueStatsService = queueStatsService;
        this.placeService = placeService;
        this.importHandler = importHandler;
        this.geocodeServiceRepository = geocodeServiceRepository;
        this.rawLocationPointProcessingTrigger = rawLocationPointProcessingTrigger;
        this.maxErrors = maxErrors;
        this.dataManagementEnabled = dataManagementEnabled;
    }

    private String getMessage(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
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
            model.addAttribute("successMessage", getMessage("message.success.token.created"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("message.error.token.creation", e.getMessage()));
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
            model.addAttribute("successMessage", getMessage("message.success.token.deleted"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("message.error.token.deletion", e.getMessage()));
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
            model.addAttribute("errorMessage", getMessage("message.error.user.self.delete"));
        } else {
            try {
                userService.deleteUser(userId);
                model.addAttribute("successMessage", getMessage("message.success.user.deleted"));
            } catch (Exception e) {
                model.addAttribute("errorMessage", getMessage("message.error.user.deletion", e.getMessage()));
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

            response.put("message", getMessage("message.success.place.updated"));
            response.put("success", true);
        } catch (Exception e) {
            response.put("message", getMessage("message.error.place.update", e.getMessage()));
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
            model.addAttribute("successMessage", getMessage("message.success.user.created"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("message.error.user.creation", e.getMessage()));
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
            model.addAttribute("successMessage", getMessage("message.success.user.updated"));

            // If the current user was updated, update the authentication
            if (isCurrentUser && !currentUsername.equals(username)) {
                // We need to re-authenticate with the new username
                model.addAttribute("requireRelogin", true);
                model.addAttribute("newUsername", username);
            }
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("message.error.user.update", e.getMessage()));
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

    @GetMapping("/language-content")
    public String getLanguageContent() {
        return "fragments/settings :: language-content";
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
    public String importGpx(@RequestParam("files") MultipartFile[] files,
                            Authentication authentication,
                            Model model) {
        User user = (User) authentication.getPrincipal();

        if (files.length == 0) {
            model.addAttribute("uploadErrorMessage", "No files selected");
            return "fragments/settings :: file-upload-content";
        }

        int totalProcessed = 0;
        int successCount = 0;
        StringBuilder errorMessages = new StringBuilder();

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                errorMessages.append("File ").append(file.getOriginalFilename()).append(" is empty. ");
                continue;
            }

            if (!file.getOriginalFilename().endsWith(".gpx")) {
                errorMessages.append("File ").append(file.getOriginalFilename()).append(" is not a GPX file. ");
                continue;
            }

            try (InputStream inputStream = file.getInputStream()) {
                Map<String, Object> result = importHandler.importGpx(inputStream, user);

                if ((Boolean) result.get("success")) {
                    totalProcessed += (Integer) result.get("pointsReceived");
                    successCount++;
                } else {
                    errorMessages.append("Error processing ").append(file.getOriginalFilename()).append(": ")
                            .append(result.get("error")).append(". ");
                }
            } catch (IOException e) {
                errorMessages.append("Error processing ").append(file.getOriginalFilename()).append(": ")
                        .append(e.getMessage()).append(". ");
            }
        }

        if (successCount > 0) {
            String message = "Successfully processed " + successCount + " file(s) with " + totalProcessed + " location points";
            if (errorMessages.length() > 0) {
                message += ". Errors: " + errorMessages.toString();
            }
            model.addAttribute("uploadSuccessMessage", message);
        } else {
            model.addAttribute("uploadErrorMessage", "No files were processed successfully. " + errorMessages.toString());
        }

        return "fragments/settings :: file-upload-content";
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

    @PostMapping("/import/geojson")
    public String importGeoJson(@RequestParam("files") MultipartFile[] files,
                                Authentication authentication,
                                Model model) {
        User user = (User) authentication.getPrincipal();

        if (files.length == 0) {
            model.addAttribute("uploadErrorMessage", "No files selected");
            return "fragments/settings :: file-upload-content";
        }

        int totalProcessed = 0;
        int successCount = 0;
        StringBuilder errorMessages = new StringBuilder();

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                errorMessages.append("File ").append(file.getOriginalFilename()).append(" is empty. ");
                continue;
            }

            String filename = file.getOriginalFilename();
            if (filename == null || (!filename.endsWith(".geojson") && !filename.endsWith(".json"))) {
                errorMessages.append("File ").append(filename).append(" is not a GeoJSON file. ");
                continue;
            }

            try (InputStream inputStream = file.getInputStream()) {
                Map<String, Object> result = importHandler.importGeoJson(inputStream, user);

                if ((Boolean) result.get("success")) {
                    totalProcessed += (Integer) result.get("pointsReceived");
                    successCount++;
                } else {
                    errorMessages.append("Error processing ").append(filename).append(": ")
                            .append(result.get("error")).append(". ");
                }
            } catch (IOException e) {
                errorMessages.append("Error processing ").append(filename).append(": ")
                        .append(e.getMessage()).append(". ");
            }
        }

        if (successCount > 0) {
            String message = "Successfully processed " + successCount + " file(s) with " + totalProcessed + " location points";
            if (errorMessages.length() > 0) {
                message += ". Errors: " + errorMessages.toString();
            }
            model.addAttribute("uploadSuccessMessage", message);
        } else {
            model.addAttribute("uploadErrorMessage", "No files were processed successfully. " + errorMessages.toString());
        }

        return "fragments/settings :: file-upload-content";
    }

    @GetMapping("/manage-data-content")
    public String getManageDataContent(Model model) {
        if (!dataManagementEnabled) {
            throw new RuntimeException("Data management is not enabled");
        }
        return "fragments/settings :: manage-data-content";
    }

    @PostMapping("/manage-data/process-visits-trips")
    public String processVisitsTrips(Authentication authentication, Model model) {
        if (!dataManagementEnabled) {
            throw new RuntimeException("Data management is not enabled");
        }

        try {

            rawLocationPointProcessingTrigger.start();

            model.addAttribute("successMessage", getMessage("data.process.success"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("data.process.error", e.getMessage()));
        }

        return "fragments/settings :: manage-data-content";
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
            model.addAttribute("successMessage", getMessage("message.success.geocode.created"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("message.error.geocode.creation", e.getMessage()));
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
