package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.dto.PlaceInfo;
import com.dedicatedcode.reitti.event.SignificantPlaceCreatedEvent;
import com.dedicatedcode.reitti.model.*;
import com.dedicatedcode.reitti.repository.*;
import com.dedicatedcode.reitti.service.*;
import com.dedicatedcode.reitti.service.processing.RawLocationPointProcessingTrigger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.LocaleResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;


@Controller
@RequestMapping("/settings")
public class SettingsController {
    private static final Logger logger = LoggerFactory.getLogger(SettingsController.class);

    private final ApiTokenService apiTokenService;
    private final UserJdbcService userJdbcService;
    private final QueueStatsService queueStatsService;
    private final PlaceService placeService;
    private final SignificantPlaceJdbcService placeJdbcService;
    private final GeocodeServiceJdbcService geocodeServiceJdbcService;
    private final ImmichIntegrationService immichIntegrationService;
    private final OwnTracksRecorderIntegrationService ownTracksRecorderIntegrationService;
    private final VisitJdbcService visitJdbcService;
    private final TripJdbcService tripJdbcService;
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final RabbitTemplate rabbitTemplate;
    private final int maxErrors;
    private final boolean dataManagementEnabled;
    private final MessageSource messageSource;
    private final LocaleResolver localeResolver;
    private final Properties gitProperties = new Properties();
    private final RawLocationPointProcessingTrigger rawLocationPointProcessingTrigger;

    public SettingsController(ApiTokenService apiTokenService,
                              UserJdbcService userJdbcService,
                              QueueStatsService queueStatsService,
                              PlaceService placeService, SignificantPlaceJdbcService placeJdbcService,
                              GeocodeServiceJdbcService geocodeServiceJdbcService,
                              ImmichIntegrationService immichIntegrationService,
                              OwnTracksRecorderIntegrationService ownTracksRecorderIntegrationService,
                              VisitJdbcService visitJdbcService,
                              TripJdbcService tripJdbcService,
                              ProcessedVisitJdbcService processedVisitJdbcService,
                              RawLocationPointJdbcService rawLocationPointJdbcService,
                              RabbitTemplate rabbitTemplate,
                              @Value("${reitti.geocoding.max-errors}") int maxErrors,
                              @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled,
                              MessageSource messageSource,
                              LocaleResolver localeResolver,
                              RawLocationPointProcessingTrigger rawLocationPointProcessingTrigger) {
        this.apiTokenService = apiTokenService;
        this.userJdbcService = userJdbcService;
        this.queueStatsService = queueStatsService;
        this.placeService = placeService;
        this.placeJdbcService = placeJdbcService;
        this.geocodeServiceJdbcService = geocodeServiceJdbcService;
        this.immichIntegrationService = immichIntegrationService;
        this.ownTracksRecorderIntegrationService = ownTracksRecorderIntegrationService;
        this.visitJdbcService = visitJdbcService;
        this.tripJdbcService = tripJdbcService;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.rabbitTemplate = rabbitTemplate;
        this.maxErrors = maxErrors;
        this.dataManagementEnabled = dataManagementEnabled;
        this.messageSource = messageSource;
        this.localeResolver = localeResolver;
        this.rawLocationPointProcessingTrigger = rawLocationPointProcessingTrigger;
        loadGitProperties();
    }

    private void loadGitProperties() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("git.properties")) {
            if (is != null) {
                this.gitProperties.load(is);
                logger.info("git.properties loaded successfully.");
            } else {
                logger.warn("git.properties not found on classpath. About section may not display Git information.");
            }
        } catch (IOException e) {
            logger.error("Failed to load git.properties", e);
        }
    }

    private String getMessage(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    @GetMapping("")
    public String settingsPage(Model model) {
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        return "settings";
    }

    @GetMapping("/api-tokens-content")
    public String getApiTokensContent(Authentication authentication, Model model) {
        String username = authentication.getName();
        User currentUser = userJdbcService.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        model.addAttribute("tokens", apiTokenService.getTokensForUser(currentUser));
        return "fragments/settings :: api-tokens-content";
    }

    @GetMapping("/users-content")
    public String getUsersContent(Authentication authentication, Model model) {
        String currentUsername = authentication.getName();
        List<User> users = userJdbcService.getAllUsers();
        model.addAttribute("users", users);
        model.addAttribute("currentUsername", currentUsername);
        return "fragments/settings :: users-content";
    }

    @GetMapping("/places-content")
    public String getPlacesContent(Authentication authentication,
                                   @RequestParam(defaultValue = "0") int page,
                                   Model model) {
        String username = authentication.getName();
        User currentUser = userJdbcService.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        Page<SignificantPlace> placesPage = placeService.getPlacesForUser(currentUser, PageRequest.of(page, 20));

        // Convert to PlaceInfo objects
        List<PlaceInfo> places = placesPage.getContent().stream()
                .map(place -> new PlaceInfo(
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
        String username = authentication.getName();
        User user = userJdbcService.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        try {
            apiTokenService.createToken(user, name);
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
        String username = authentication.getName();
        User user = userJdbcService.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

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
        User currentUser = userJdbcService.findByUsername(currentUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + currentUsername));

        // Prevent self-deletion
        if (currentUser.getId().equals(userId)) {
            model.addAttribute("errorMessage", getMessage("message.error.user.self.delete"));
        } else {
            try {
                userJdbcService.deleteUser(userId);
                model.addAttribute("successMessage", getMessage("message.success.user.deleted"));
            } catch (Exception e) {
                model.addAttribute("errorMessage", getMessage("message.error.user.deletion", e.getMessage()));
            }
        }

        // Get updated user list and add to model
        List<User> users = userJdbcService.getAllUsers();
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

        User user = (User) authentication.getPrincipal();
        Map<String, Object> response = new HashMap<>();
        if (this.placeJdbcService.exists(user, placeId)) {
            try {
                SignificantPlace significantPlace = placeJdbcService.findById(placeId).orElseThrow();
                placeJdbcService.update(significantPlace.withName(name));

                response.put("message", getMessage("message.success.place.updated"));
                response.put("success", true);
            } catch (Exception e) {
                response.put("message", getMessage("message.error.place.update", e.getMessage()));
                response.put("success", false);
            }
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return response;
    }

    @PostMapping("/places/{placeId}/geocode")
    @ResponseBody
    public Map<String, Object> geocodePlace(@PathVariable Long placeId,
                                            Authentication authentication) {

        User user = (User) authentication.getPrincipal();
        Map<String, Object> response = new HashMap<>();
        if (this.placeJdbcService.exists(user, placeId)) {
            try {
                SignificantPlace significantPlace = placeJdbcService.findById(placeId).orElseThrow();
                
                // Clear geocoding data and mark as not geocoded
                SignificantPlace clearedPlace = significantPlace.withGeocoded(false).withAddress(null);
                placeJdbcService.update(clearedPlace);
                
                // Send SignificantPlaceCreatedEvent to trigger geocoding
                SignificantPlaceCreatedEvent event = new SignificantPlaceCreatedEvent(
                    significantPlace.getId(), 
                    significantPlace.getLatitudeCentroid(),
                    significantPlace.getLongitudeCentroid()
                );
                rabbitTemplate.convertAndSend(RabbitMQConfig.SIGNIFICANT_PLACE_QUEUE, event);

                response.put("message", getMessage("places.geocode.success"));
                response.put("success", true);
            } catch (Exception e) {
                response.put("message", getMessage("places.geocode.error", e.getMessage()));
                response.put("success", false);
            }
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
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
            userJdbcService.createUser(username, displayName, password);
            model.addAttribute("successMessage", getMessage("message.success.user.created"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("message.error.user.creation", e.getMessage()));
        }

        // Get updated user list and add to model
        List<User> users = userJdbcService.getAllUsers();
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
        User currentUser = userJdbcService.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
        boolean isCurrentUser = currentUser.getId().equals(userId);

        try {
            userJdbcService.updateUser(userId, username, displayName, password);
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
        List<User> users = userJdbcService.getAllUsers();
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

    @GetMapping("/language-content")
    public String getLanguageContent() {
        return "fragments/settings :: language-content";
    }

    @PostMapping("/language")
    public String changeLanguage(@RequestParam String lang, 
                                HttpServletRequest request, 
                                HttpServletResponse response, 
                                Model model) {
        try {
            Locale locale = Locale.forLanguageTag(lang);
            localeResolver.setLocale(request, response, locale);
            model.addAttribute("successMessage", getMessage("message.success.language.changed"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("message.error.language.change", e.getMessage()));
        }
        
        return "fragments/settings :: language-content";
    }

    @GetMapping("/integrations-content")
    public String getIntegrationsContent(Authentication authentication, Model model, HttpServletRequest request,
                                        @RequestParam(required = false) String openSection) {
        String username = authentication.getName();
        User currentUser = userJdbcService.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        List<ApiToken> tokens = apiTokenService.getTokensForUser(currentUser);

        // Add the first token if available
        if (!tokens.isEmpty()) {
            model.addAttribute("firstToken", tokens.getFirst().getToken());
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

        Optional<OwnTracksRecorderIntegration> recorderIntegration = ownTracksRecorderIntegrationService.getIntegrationForUser(currentUser);
        if (recorderIntegration.isPresent()) {
            model.addAttribute("ownTracksRecorderIntegration", recorderIntegration.get());
            model.addAttribute("hasRecorderIntegration", recorderIntegration.get().isEnabled());
        } else {
            model.addAttribute("hasRecorderIntegration", false);
        }

        // Add the open section parameter
        model.addAttribute("openSection", openSection);

        return "fragments/settings :: integrations-content";
    }

    @GetMapping("/photos-content")
    public String getPhotosContent(Authentication authentication, Model model) {
        String username = authentication.getName();
        User currentUser = userJdbcService.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        Optional<ImmichIntegration> integration = immichIntegrationService.getIntegrationForUser(currentUser);
        
        if (integration.isPresent()) {
            model.addAttribute("immichIntegration", integration.get());
            model.addAttribute("hasIntegration", true);
        } else {
            model.addAttribute("hasIntegration", false);
        }
        
        return "fragments/settings :: photos-content";
    }

    @PostMapping("/immich-integration")
    public String saveImmichIntegration(@RequestParam String serverUrl,
                                       @RequestParam String apiToken,
                                       @RequestParam(defaultValue = "false") boolean enabled,
                                       Authentication authentication,
                                       Model model) {
        String username = authentication.getName();
        User currentUser = userJdbcService.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        
        try {
            ImmichIntegration integration = immichIntegrationService.saveIntegration(
                currentUser, serverUrl, apiToken, enabled);
            
            model.addAttribute("immichIntegration", integration);
            model.addAttribute("hasIntegration", true);
            model.addAttribute("successMessage", getMessage("integrations.immich.config.saved"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("integrations.immich.config.error", e.getMessage()));
            // Re-populate form with submitted values
            ImmichIntegration tempIntegration = new ImmichIntegration(serverUrl, apiToken, enabled);
            model.addAttribute("immichIntegration", tempIntegration);
            model.addAttribute("hasIntegration", true);
        }
        
        return "fragments/settings :: photos-content";
    }

    @PostMapping("/immich-integration/test")
    @ResponseBody
    public Map<String, Object> testImmichConnection(@RequestParam String serverUrl,
                                                   @RequestParam String apiToken) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean connectionSuccessful = immichIntegrationService.testConnection(serverUrl, apiToken);
            
            if (connectionSuccessful) {
                response.put("success", true);
                response.put("message", getMessage("integrations.immich.connection.success"));
            } else {
                response.put("success", false);
                response.put("message", getMessage("integrations.immich.connection.failed", "Invalid configuration"));
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", getMessage("integrations.immich.connection.failed", e.getMessage()));
        }
        
        return response;
    }

    @PostMapping("/owntracks-recorder-integration")
    public String saveOwnTracksRecorderIntegration(@RequestParam String baseUrl,
                                                  @RequestParam String username,
                                                  @RequestParam String deviceId,
                                                  @RequestParam(defaultValue = "false") boolean enabled,
                                                  Authentication authentication,
                                                  Model model,
                                                  HttpServletRequest request) {
        String currentUsername = authentication.getName();
        User currentUser = userJdbcService.findByUsername(currentUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + currentUsername));


        List<ApiToken> tokens = apiTokenService.getTokensForUser(currentUser);

        if (!tokens.isEmpty()) {
            model.addAttribute("firstToken", tokens.getFirst().getToken());
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

        if ((scheme.equals("http") && serverPort != 80) ||
                (scheme.equals("https") && serverPort != 443)) {
            serverUrl.append(":").append(serverPort);
        }

        model.addAttribute("serverUrl", serverUrl.toString());
        
        try {
            OwnTracksRecorderIntegration integration = ownTracksRecorderIntegrationService.saveIntegration(
                currentUser, baseUrl, username, deviceId, enabled);
            
            model.addAttribute("successMessage", getMessage("integrations.owntracks.recorder.config.saved"));
            model.addAttribute("ownTracksRecorderIntegration", integration);
            model.addAttribute("hasRecorderIntegration", enabled);
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("integrations.owntracks.recorder.config.error", e.getMessage()));
            
            // Re-populate form with submitted values for error case
            OwnTracksRecorderIntegration tempIntegration = new OwnTracksRecorderIntegration(baseUrl, username, deviceId, enabled);
            model.addAttribute("ownTracksRecorderIntegration", tempIntegration);
            model.addAttribute("hasRecorderIntegration", enabled);
        }
        
        // Keep external data stores section open
        model.addAttribute("openSection", "external-data-stores");
        
        return "fragments/settings :: integrations-content";
    }

    @PostMapping("/owntracks-recorder-integration/test")
    @ResponseBody
    public Map<String, Object> testOwnTracksRecorderConnection(@RequestParam String baseUrl,
                                                              @RequestParam String username,
                                                              @RequestParam String deviceId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean connectionSuccessful = ownTracksRecorderIntegrationService.testConnection(baseUrl, username, deviceId);
            
            if (connectionSuccessful) {
                response.put("success", true);
                response.put("message", getMessage("integrations.owntracks.recorder.connection.success"));
            } else {
                response.put("success", false);
                response.put("message", getMessage("integrations.owntracks.recorder.connection.failed", "Invalid configuration"));
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", getMessage("integrations.owntracks.recorder.connection.failed", e.getMessage()));
        }
        
        return response;
    }

    @PostMapping("/owntracks-recorder-integration/delete")
    public String deleteOwnTracksRecorderIntegration(Authentication authentication, Model model, HttpServletRequest request) {
        String currentUsername = authentication.getName();
        User currentUser = userJdbcService.findByUsername(currentUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + currentUsername));

        try {
            ownTracksRecorderIntegrationService.deleteIntegration(currentUser);
            model.addAttribute("successMessage", getMessage("integrations.owntracks.recorder.config.deleted"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("integrations.owntracks.recorder.config.delete.error", e.getMessage()));
        }

        // Re-populate the integrations content
        List<ApiToken> tokens = apiTokenService.getTokensForUser(currentUser);
        if (!tokens.isEmpty()) {
            model.addAttribute("firstToken", tokens.getFirst().getToken());
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

        if ((scheme.equals("http") && serverPort != 80) ||
                (scheme.equals("https") && serverPort != 443)) {
            serverUrl.append(":").append(serverPort);
        }

        model.addAttribute("serverUrl", serverUrl.toString());
        model.addAttribute("hasRecorderIntegration", false);

        return "fragments/settings :: integrations-content";
    }

    @PostMapping("/owntracks-recorder-integration/load-historical")
    public String loadOwnTracksRecorderHistoricalData(Authentication authentication, Model model, HttpServletRequest request) {
        String currentUsername = authentication.getName();
        User currentUser = userJdbcService.findByUsername(currentUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + currentUsername));

        try {
            ownTracksRecorderIntegrationService.loadHistoricalData(currentUser);
            model.addAttribute("successMessage", getMessage("integrations.owntracks.recorder.load.historical.success"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("integrations.owntracks.recorder.load.historical.error", e.getMessage()));
        }

        // Re-populate the integrations content
        List<ApiToken> tokens = apiTokenService.getTokensForUser(currentUser);
        if (!tokens.isEmpty()) {
            model.addAttribute("firstToken", tokens.getFirst().getToken());
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

        if ((scheme.equals("http") && serverPort != 80) ||
                (scheme.equals("https") && serverPort != 443)) {
            serverUrl.append(":").append(serverPort);
        }

        model.addAttribute("serverUrl", serverUrl.toString());

        Optional<OwnTracksRecorderIntegration> recorderIntegration = ownTracksRecorderIntegrationService.getIntegrationForUser(currentUser);
        if (recorderIntegration.isPresent()) {
            model.addAttribute("ownTracksRecorderIntegration", recorderIntegration.get());
            model.addAttribute("hasRecorderIntegration", recorderIntegration.get().isEnabled());
        } else {
            model.addAttribute("hasRecorderIntegration", false);
        }

        // Keep external data stores section open
        model.addAttribute("openSection", "external-data-stores");

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

    @GetMapping("/manage-data-content")
    public String getManageDataContent(Model model) {
        if (!dataManagementEnabled) {
            throw new RuntimeException("Data management is not enabled");
        }
        return "fragments/settings :: manage-data-content";
    }

    @PostMapping("/manage-data/process-visits-trips")
    public String processVisitsTrips(Model model) {
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

    @PostMapping("/manage-data/clear-and-reprocess")
    public String clearAndReprocess(Authentication authentication, Model model) {
        if (!dataManagementEnabled) {
            throw new RuntimeException("Data management is not enabled");
        }

        try {
            String username = authentication.getName();
            User currentUser = userJdbcService.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
            
            // Clear all processed data except SignificantPlaces
            // This would need to be implemented in appropriate service classes
            // For now, we'll assume these methods exist or need to be created
            clearProcessedDataExceptPlaces(currentUser);
            
            // Mark all raw location points as unprocessed
            markRawLocationPointsAsUnprocessed(currentUser);
            
            rawLocationPointProcessingTrigger.start();
            
            model.addAttribute("successMessage", getMessage("data.clear.reprocess.success"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("data.clear.reprocess.error", e.getMessage()));
        }

        return "fragments/settings :: manage-data-content";
    }

    @PostMapping("/manage-data/remove-all-data")
    public String removeAllData(Authentication authentication, Model model) {
        if (!dataManagementEnabled) {
            throw new RuntimeException("Data management is not enabled");
        }

        try {
            String username = authentication.getName();
            User currentUser = userJdbcService.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
            
            removeAllDataExceptPlaces(currentUser);
            
            model.addAttribute("successMessage", getMessage("data.remove.all.success"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("data.remove.all.error", e.getMessage()));
        }

        return "fragments/settings :: manage-data-content";
    }

    private void clearProcessedDataExceptPlaces(User user) {
        tripJdbcService.deleteAllForUser(user);
        processedVisitJdbcService.deleteAllForUser(user);
        visitJdbcService.deleteAllForUser(user);
    }

    private void markRawLocationPointsAsUnprocessed(User user) {
        rawLocationPointJdbcService.markAllAsUnprocessedForUser(user);
    }

    private void removeAllDataExceptPlaces(User user) {
        tripJdbcService.deleteAllForUser(user);
        processedVisitJdbcService.deleteAllForUser(user);
        visitJdbcService.deleteAllForUser(user);
        rawLocationPointJdbcService.deleteAllForUser(user);
    }

    @GetMapping("/geocode-services-content")
    public String getGeocodeServicesContent(Model model) {
        model.addAttribute("geocodeServices", geocodeServiceJdbcService.findAllByOrderByNameAsc());
        model.addAttribute("maxErrors", maxErrors);
        return "fragments/settings :: geocode-services-content";
    }

    @PostMapping("/geocode-services")
    public String createGeocodeService(@RequestParam String name,
                                       @RequestParam String urlTemplate,
                                       Model model) {
        try {
            RemoteGeocodeService service = new RemoteGeocodeService(name, urlTemplate, true, 0, null, null);
            geocodeServiceJdbcService.save(service);
            model.addAttribute("successMessage", getMessage("message.success.geocode.created"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("message.error.geocode.creation", e.getMessage()));
        }

        model.addAttribute("geocodeServices", geocodeServiceJdbcService.findAllByOrderByNameAsc());
        model.addAttribute("maxErrors", maxErrors);
        return "fragments/settings :: geocode-services-content";
    }

    @PostMapping("/geocode-services/{id}/toggle")
    public String toggleGeocodeService(@PathVariable Long id, Model model) {
        RemoteGeocodeService service = geocodeServiceJdbcService.findById(id).orElseThrow();
        service = service.withEnabled(!service.isEnabled());
        if (service.isEnabled()) {
            service = service.resetErrorCount();
        }
        geocodeServiceJdbcService.save(service);
        model.addAttribute("geocodeServices", geocodeServiceJdbcService.findAllByOrderByNameAsc());
        model.addAttribute("maxErrors", maxErrors);
        return "fragments/settings :: geocode-services-content";
    }

    @PostMapping("/geocode-services/{id}/delete")
    public String deleteGeocodeService(@PathVariable Long id, Model model) {
        RemoteGeocodeService service = geocodeServiceJdbcService.findById(id).orElseThrow();
        geocodeServiceJdbcService.delete(service);
        model.addAttribute("geocodeServices", geocodeServiceJdbcService.findAllByOrderByNameAsc());
        model.addAttribute("maxErrors", maxErrors);
        return "fragments/settings :: geocode-services-content";
    }

    @PostMapping("/geocode-services/{id}/reset-errors")
    public String resetGeocodeServiceErrors(@PathVariable Long id, Model model) {
        RemoteGeocodeService service = geocodeServiceJdbcService.findById(id).orElseThrow();
        geocodeServiceJdbcService.save(service.resetErrorCount().withEnabled(true));
        model.addAttribute("geocodeServices", geocodeServiceJdbcService.findAllByOrderByNameAsc());
        model.addAttribute("maxErrors", maxErrors);
        return "fragments/settings :: geocode-services-content";
    }

    @PostMapping("/geocode-services/run-geocoding")
    public String runGeocoding(Authentication authentication, Model model) {
        try {
            String username = authentication.getName();
            User currentUser = userJdbcService.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
            
            // Find all non-geocoded significant places for the user
            List<SignificantPlace> nonGeocodedPlaces = placeJdbcService.findNonGeocodedByUser(currentUser);
            
            if (nonGeocodedPlaces.isEmpty()) {
                model.addAttribute("successMessage", getMessage("geocoding.no.places"));
            } else {
                // Send SignificantPlaceCreatedEvent for each non-geocoded place
                for (SignificantPlace place : nonGeocodedPlaces) {
                    SignificantPlaceCreatedEvent event = new SignificantPlaceCreatedEvent(
                        place.getId(), 
                        place.getLatitudeCentroid(),
                        place.getLongitudeCentroid()
                    );
                    rabbitTemplate.convertAndSend(RabbitMQConfig.SIGNIFICANT_PLACE_QUEUE, event);
                }
                
                model.addAttribute("successMessage", getMessage("geocoding.run.success", nonGeocodedPlaces.size()));
            }
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("geocoding.run.error", e.getMessage()));
        }

        model.addAttribute("geocodeServices", geocodeServiceJdbcService.findAllByOrderByNameAsc());
        model.addAttribute("maxErrors", maxErrors);
        return "fragments/settings :: geocode-services-content";
    }

    @PostMapping("/geocode-services/clear-and-rerun")
    public String clearAndRerunGeocoding(Authentication authentication, Model model) {
        try {
            String username = authentication.getName();
            User currentUser = userJdbcService.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
            
            // Find all significant places for the user
            List<SignificantPlace> allPlaces = placeJdbcService.findAllByUser(currentUser);
            
            if (allPlaces.isEmpty()) {
                model.addAttribute("successMessage", getMessage("geocoding.no.places"));
            } else {
                // Clear geocoding data for all places
                for (SignificantPlace place : allPlaces) {
                    SignificantPlace clearedPlace = place.withGeocoded(false).withAddress(null);
                    placeJdbcService.update(clearedPlace);
                }
                
                // Send SignificantPlaceCreatedEvent for each place
                for (SignificantPlace place : allPlaces) {
                    SignificantPlaceCreatedEvent event = new SignificantPlaceCreatedEvent(
                        place.getId(), 
                        place.getLatitudeCentroid(),
                        place.getLongitudeCentroid()
                    );
                    rabbitTemplate.convertAndSend(RabbitMQConfig.SIGNIFICANT_PLACE_QUEUE, event);
                }
                
                model.addAttribute("successMessage", getMessage("geocoding.clear.success", allPlaces.size()));
            }
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("geocoding.clear.error", e.getMessage()));
        }

        model.addAttribute("geocodeServices", geocodeServiceJdbcService.findAllByOrderByNameAsc());
        model.addAttribute("maxErrors", maxErrors);
        return "fragments/settings :: geocode-services-content";
    }

    @GetMapping("/about-content")
    public String getAboutContent(Model model) {
        String notAvailable = getMessage("about.not.available");
        String property = gitProperties.getProperty("git.tags");
        if (!StringUtils.hasText(property)) {
            property = "development";
        }
        model.addAttribute("buildVersion", property);

        String commitId = gitProperties.getProperty("git.commit.id.abbrev", notAvailable);
        String commitTime = gitProperties.getProperty("git.commit.time");

        StringBuilder commitDetails = new StringBuilder();
        if (!commitId.equals(notAvailable)) {
            commitDetails.append(commitId);
            if (commitTime != null && !commitTime.isEmpty()) {
                commitDetails.append(" (").append(commitTime);
                commitDetails.append(")");
            }
        } else {
            commitDetails.append(notAvailable);
        }


        model.addAttribute("gitCommitDetails", commitDetails.toString());
        model.addAttribute("buildTime", gitProperties.getProperty("git.build.time", notAvailable));
        return "fragments/settings :: about-content";
    }
}
