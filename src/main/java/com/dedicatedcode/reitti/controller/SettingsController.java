package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.dto.PlaceInfo;
import com.dedicatedcode.reitti.event.SignificantPlaceCreatedEvent;
import com.dedicatedcode.reitti.model.*;
import com.dedicatedcode.reitti.repository.*;
import com.dedicatedcode.reitti.service.*;
import com.dedicatedcode.reitti.service.integration.ImmichIntegrationService;
import com.dedicatedcode.reitti.service.integration.OwnTracksRecorderIntegrationService;
import com.dedicatedcode.reitti.service.processing.ProcessingPipelineTrigger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;


@Controller
@RequestMapping("/settings")
public class SettingsController {
    private final ApiTokenService apiTokenService;
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
    private final ProcessingPipelineTrigger processingPipelineTrigger;

    private final UserJdbcService userJdbcService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final AvatarService avatarService;
    private final VersionService versionService;

    public SettingsController(ApiTokenService apiTokenService,
                              UserJdbcService userJdbcService,
                              UserSettingsJdbcService userSettingsJdbcService,
                              AvatarService avatarService,
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
                              ProcessingPipelineTrigger processingPipelineTrigger,
                              VersionService versionService) {
        this.apiTokenService = apiTokenService;
        this.userJdbcService = userJdbcService;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.avatarService = avatarService;
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
        this.processingPipelineTrigger = processingPipelineTrigger;
        this.versionService = versionService;
    }

    private String getMessage(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    @GetMapping("")
    public String settingsPage(@AuthenticationPrincipal User user,
                               @RequestParam(required = false, defaultValue = "job-status") String section,
                               HttpServletRequest request,
                               Model model) {
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        model.addAttribute("activeSection", section);
        
        // Load the content for the active section immediately
        switch (section) {
            case "api-tokens":
                getApiTokensContent(user, model);
                break;
            case "user-management":
                return getUserManagementPage(user, model);
            case "places-management":
                getPlacesContent(user, 0, model);
                break;
            case "geocode-services":
                getGeocodeServicesContent(model);
                break;
            case "integrations":
                getIntegrationsContent(user, request, model, null);
                break;
            case "manage-data":
                if (dataManagementEnabled) {
                    getManageDataContent(model);
                }
                break;
            case "file-upload":
                // File upload content will be loaded via HTMX as before
                break;
            case "about-section":
                getAboutContent(model);
                break;
            case "job-status":
            default:
                getQueueStatsContent(model);
                break;
        }
        
        return "settings";
    }

    @GetMapping("/api-tokens-content")
    public String getApiTokensContent(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("tokens", apiTokenService.getTokensForUser(user));
        model.addAttribute("recentUsages", apiTokenService.getRecentUsagesForUser(user, 10));
        model.addAttribute("maxUsagesToShow", 10);
        return "fragments/api-tokens :: api-tokens-content";
    }


    @GetMapping("/places-content")
    public String getPlacesContent(@AuthenticationPrincipal User user,
                                   @RequestParam(defaultValue = "0") int page,
                                   Model model) {
        Page<SignificantPlace> placesPage = placeService.getPlacesForUser(user, PageRequest.of(page, 20));

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
        model.addAttribute("recentUsages", apiTokenService.getRecentUsagesForUser(user, 10));
        model.addAttribute("maxUsagesToShow", 10);

        // Return the api-tokens-content fragment
        return "fragments/api-tokens :: api-tokens-content";
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
        model.addAttribute("recentUsages", apiTokenService.getRecentUsagesForUser(user, 10));
        model.addAttribute("maxUsagesToShow", 10);

        // Return the api-tokens-content fragment
        return "fragments/api-tokens :: api-tokens-content";
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

    @GetMapping("/queue-stats-content")
    public String getQueueStatsContent(Model model) {
        model.addAttribute("queueStats", queueStatsService.getQueueStats());
        return "fragments/settings :: queue-stats-content";
    }

    @GetMapping("/integrations-content")
    public String getIntegrationsContent(@AuthenticationPrincipal User currentUser,
                                         HttpServletRequest request,
                                         Model model,
                                         @RequestParam(required = false) String openSection) {
        List<ApiToken> tokens = apiTokenService.getTokensForUser(currentUser);

        // Add the first token if available
        if (!tokens.isEmpty()) {
            model.addAttribute("firstToken", tokens.getFirst().getToken());
            model.addAttribute("hasToken", true);
        } else {
            model.addAttribute("hasToken", false);
        }

        Optional<OwnTracksRecorderIntegration> recorderIntegration = ownTracksRecorderIntegrationService.getIntegrationForUser(currentUser);
        if (recorderIntegration.isPresent()) {
            model.addAttribute("ownTracksRecorderIntegration", recorderIntegration.get());
            model.addAttribute("hasRecorderIntegration", recorderIntegration.get().isEnabled());
        } else {
            model.addAttribute("hasRecorderIntegration", false);
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

        model.addAttribute("openSection", openSection);
        model.addAttribute("serverUrl", serverUrl.toString());

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
            processingPipelineTrigger.start();
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
            
            processingPipelineTrigger.start();
            
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
        model.addAttribute("buildVersion", this.versionService.getVersion());
        model.addAttribute("gitCommitDetails", this.versionService.getCommitDetails());
        model.addAttribute("buildTime", this.versionService.getBuildTime());
        return "fragments/settings :: about-content";
    }

    private String getUserManagementPage(@AuthenticationPrincipal User user, Model model) {
        if (user.getRole() != Role.ADMIN) {
            // For non-admin users, show their own form
            model.addAttribute("userId", user.getId());
            model.addAttribute("username", user.getUsername());
            model.addAttribute("displayName", user.getDisplayName());
            model.addAttribute("selectedRole", user.getRole());
            ;

            UserSettings userSettings = userSettingsJdbcService.findByUserId(user.getId())
                    .orElse(UserSettings.defaultSettings(user.getId()));
            model.addAttribute("selectedLanguage", userSettings.getSelectedLanguage());
            model.addAttribute("selectedUnitSystem", userSettings.getUnitSystem().name());
            model.addAttribute("preferColoredMap", userSettings.isPreferColoredMap());
            model.addAttribute("homeLatitude", userSettings.getHomeLatitude());
            model.addAttribute("homeLongitude", userSettings.getHomeLongitude());
            model.addAttribute("unitSystems", UnitSystem.values());
            model.addAttribute("isAdmin", false);

            // Check if user has avatar
            boolean hasAvatar = this.avatarService.getInfo(user.getId()).isPresent();
            model.addAttribute("hasAvatar", hasAvatar);

            // Add default avatars to model
            model.addAttribute("defaultAvatars", Arrays.asList(
                    "avatar_man.jpg", "avatar_woman.jpg", "avatar_boy.jpg", "avatar_girl.jpg"
            ));
        } else {
            // For admin users, show user list
            List<User> users = userJdbcService.getAllUsers();
            model.addAttribute("users", users);
            model.addAttribute("currentUsername", user.getUsername());
            model.addAttribute("isAdmin", true);
        }

        return "settings";
    }

}
