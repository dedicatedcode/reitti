package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.dto.PlaceInfo;
import com.dedicatedcode.reitti.event.SignificantPlaceCreatedEvent;
import com.dedicatedcode.reitti.model.*;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geocoding.GeocodingResponse;
import com.dedicatedcode.reitti.model.geocoding.RemoteGeocodeService;
import com.dedicatedcode.reitti.model.integration.ImmichIntegration;
import com.dedicatedcode.reitti.model.integration.OwnTracksRecorderIntegration;
import com.dedicatedcode.reitti.model.security.ApiToken;
import com.dedicatedcode.reitti.model.security.MagicLinkAccessLevel;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSettings;
import com.dedicatedcode.reitti.repository.*;
import com.dedicatedcode.reitti.service.*;
import com.dedicatedcode.reitti.service.integration.ImmichIntegrationService;
import com.dedicatedcode.reitti.service.integration.OwnTracksRecorderIntegrationService;
import com.dedicatedcode.reitti.service.processing.ProcessingPipelineTrigger;
import com.dedicatedcode.reitti.repository.GeocodingResponseJdbcService;
import jakarta.servlet.http.HttpServletRequest;
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

import java.time.ZoneId;
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
    private final GeocodingResponseJdbcService geocodingResponseJdbcService;
    private final ImmichIntegrationService immichIntegrationService;
    private final OwnTracksRecorderIntegrationService ownTracksRecorderIntegrationService;
    private final VisitJdbcService visitJdbcService;
    private final TripJdbcService tripJdbcService;
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final MagicLinkJdbcService magicLinkJdbcService;
    private final RabbitTemplate rabbitTemplate;
    private final int maxErrors;
    private final boolean dataManagementEnabled;
    private final MessageSource messageSource;
    private final ProcessingPipelineTrigger processingPipelineTrigger;

    private final UserJdbcService userJdbcService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final AvatarService avatarService;
    private final VersionService versionService;
    private final boolean localLoginDisabled;
    private final boolean oidcEnabled;

    public SettingsController(ApiTokenService apiTokenService,
                              UserJdbcService userJdbcService,
                              UserSettingsJdbcService userSettingsJdbcService,
                              AvatarService avatarService,
                              QueueStatsService queueStatsService,
                              PlaceService placeService, SignificantPlaceJdbcService placeJdbcService,
                              GeocodeServiceJdbcService geocodeServiceJdbcService,
                              GeocodingResponseJdbcService geocodingResponseJdbcService,
                              ImmichIntegrationService immichIntegrationService,
                              OwnTracksRecorderIntegrationService ownTracksRecorderIntegrationService,
                              VisitJdbcService visitJdbcService,
                              TripJdbcService tripJdbcService,
                              ProcessedVisitJdbcService processedVisitJdbcService,
                              RawLocationPointJdbcService rawLocationPointJdbcService, MagicLinkJdbcService magicLinkJdbcService,
                              RabbitTemplate rabbitTemplate,
                              @Value("${reitti.geocoding.max-errors}") int maxErrors,
                              @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled,
                              MessageSource messageSource,
                              ProcessingPipelineTrigger processingPipelineTrigger,
                              VersionService versionService,
                              @Value("${reitti.security.oidc.enabled:false}") boolean oidcEnabled,
                              @Value("${reitti.security.local-login.disable}") boolean localLoginDisabled) {
        this.apiTokenService = apiTokenService;
        this.userJdbcService = userJdbcService;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.avatarService = avatarService;
        this.queueStatsService = queueStatsService;
        this.placeService = placeService;
        this.placeJdbcService = placeJdbcService;
        this.geocodeServiceJdbcService = geocodeServiceJdbcService;
        this.geocodingResponseJdbcService = geocodingResponseJdbcService;
        this.immichIntegrationService = immichIntegrationService;
        this.ownTracksRecorderIntegrationService = ownTracksRecorderIntegrationService;
        this.visitJdbcService = visitJdbcService;
        this.tripJdbcService = tripJdbcService;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.magicLinkJdbcService = magicLinkJdbcService;
        this.rabbitTemplate = rabbitTemplate;
        this.maxErrors = maxErrors;
        this.dataManagementEnabled = dataManagementEnabled;
        this.messageSource = messageSource;
        this.processingPipelineTrigger = processingPipelineTrigger;
        this.versionService = versionService;
        this.oidcEnabled = oidcEnabled;
        this.localLoginDisabled = localLoginDisabled;
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
            case "sharing":
                getSharingContent(user, model);
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
            case "export-data":
                getExportDataContent(user, model);
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

    @GetMapping("/sharing-content")
    public String getSharingContent(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("tokens", this.magicLinkJdbcService.findByUser(user));
        model.addAttribute("accessLevels", MagicLinkAccessLevel.values());
        return "fragments/magic-links :: magic-links-content";
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
                        place.getType(),
                        place.getLatitudeCentroid(),
                        place.getLongitudeCentroid()
                ))
                .collect(Collectors.toList());

        // Add pagination info to model
        model.addAttribute("currentPage", placesPage.getNumber());
        model.addAttribute("totalPages", placesPage.getTotalPages());
        model.addAttribute("places", places);
        model.addAttribute("isEmpty", places.isEmpty());
        model.addAttribute("placeTypes", SignificantPlace.PlaceType.values());

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
    public String updatePlace(@PathVariable Long placeId,
                              @RequestParam String name,
                              @RequestParam(required = false) String address,
                              @RequestParam(required = false) String type,
                              @RequestParam(defaultValue = "0") int page,
                              Authentication authentication,
                              Model model) {

        User user = (User) authentication.getPrincipal();
        if (this.placeJdbcService.exists(user, placeId)) {
            try {
                SignificantPlace significantPlace = placeJdbcService.findById(placeId).orElseThrow();
                SignificantPlace updatedPlace = significantPlace.withName(name);

                // Update address if provided
                if (address != null) {
                    updatedPlace = updatedPlace.withAddress(address.trim().isEmpty() ? null : address.trim());
                }

                if (type != null && !type.isEmpty()) {
                    try {
                        SignificantPlace.PlaceType placeType = SignificantPlace.PlaceType.valueOf(type);
                        updatedPlace = updatedPlace.withType(placeType);
                    } catch (IllegalArgumentException e) {
                        model.addAttribute("errorMessage", getMessage("message.error.place.update", "Invalid place type"));
                        return getPlacesContent(user, page, model);
                    }
                }

                placeJdbcService.update(updatedPlace);
                model.addAttribute("successMessage", getMessage("message.success.place.updated"));
            } catch (Exception e) {
                model.addAttribute("errorMessage", getMessage("message.error.place.update", e.getMessage()));
            }
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        
        return getPlacesContent(user, page, model);
    }

    @PostMapping("/places/{placeId}/geocode")
    public String geocodePlace(@PathVariable Long placeId,
                               @RequestParam(defaultValue = "0") int page,
                               Authentication authentication,
                               Model model) {

        User user = (User) authentication.getPrincipal();
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
                rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.SIGNIFICANT_PLACE_ROUTING_KEY, event);

                model.addAttribute("successMessage", getMessage("places.geocode.success"));
            } catch (Exception e) {
                model.addAttribute("errorMessage", getMessage("places.geocode.error", e.getMessage()));
            }
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        
        return getPlacesContent(user, page, model);
    }

    @GetMapping("/places/{placeId}/geocoding-response")
    public String getGeocodingResponse(@PathVariable Long placeId,
                                       @RequestParam(defaultValue = "0") int page,
                                       Authentication authentication,
                                       Model model) {

        User user = (User) authentication.getPrincipal();
        if (!this.placeJdbcService.exists(user, placeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        try {
            SignificantPlace place = placeJdbcService.findById(placeId).orElseThrow();
            
            // Convert to PlaceInfo for the template
            PlaceInfo placeInfo = new PlaceInfo(
                place.getId(),
                place.getName(),
                place.getAddress(),
                place.getType(),
                place.getLatitudeCentroid(),
                place.getLongitudeCentroid()
            );
            
            // Get all geocoding responses for this place
            List<GeocodingResponse> geocodingResponses = geocodingResponseJdbcService.findBySignificantPlace(place);
            
            model.addAttribute("place", placeInfo);
            model.addAttribute("currentPage", page);
            model.addAttribute("geocodingResponses", geocodingResponses);
            
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("message.error.place.update", e.getMessage()));
            return getPlacesContent(user, page, model);
        }
        
        return "fragments/settings :: geocoding-response-content";
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
            IntegrationTestResult result = immichIntegrationService.testConnection(serverUrl, apiToken);
            
            if (result.success()) {
                response.put("success", true);
                response.put("message", getMessage("integrations.immich.connection.success"));
            } else {
                response.put("success", false);
                response.put("message", getMessage("integrations.immich.connection.failed", result.message()));
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
                                                  Model model) {
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
                    rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.SIGNIFICANT_PLACE_ROUTING_KEY, event);
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
                    rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.SIGNIFICANT_PLACE_ROUTING_KEY, event);
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

    @GetMapping("/data-quality-content")
    public String getDataQualityContent(@AuthenticationPrincipal User user, Model model) {
        try {
            DataQualityReport dataQuality = generateDataQualityReport(user);
            model.addAttribute("dataQuality", dataQuality);
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("integrations.data.quality.error", e.getMessage()));
        }
        return "fragments/settings :: data-quality-content";
    }

    private DataQualityReport generateDataQualityReport(User user) {
        java.time.Instant now = java.time.Instant.now();
        java.time.Instant oneDayAgo = now.minus(24, java.time.temporal.ChronoUnit.HOURS);
        java.time.Instant sevenDaysAgo = now.minus(7, java.time.temporal.ChronoUnit.DAYS);

        // Get location points for different time periods
        List<RawLocationPoint> allPoints = rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(
            user, sevenDaysAgo, now);
        
        List<RawLocationPoint> last24hPoints = allPoints.stream()
            .filter(point -> point.getTimestamp().isAfter(oneDayAgo))
            .toList();

        // Calculate basic statistics
        long totalPoints = rawLocationPointJdbcService.countByUser(user);
        int pointsLast24h = last24hPoints.size();
        int pointsLast7d = allPoints.size();
        int avgPointsPerDay = pointsLast7d > 0 ? pointsLast7d / 7 : 0;

        // Find latest point
        String latestPointTime = null;
        String timeSinceLastPoint = null;
        if (!allPoints.isEmpty()) {
            RawLocationPoint latestPoint = allPoints.getLast();
            latestPointTime = latestPoint.getTimestamp().toString();
            
            long minutesSince = java.time.Duration.between(latestPoint.getTimestamp(), now).toMinutes();
            if (minutesSince < 60) {
                timeSinceLastPoint = minutesSince + " minutes ago";
            } else if (minutesSince < 1440) {
                timeSinceLastPoint = (minutesSince / 60) + " hours ago";
            } else {
                timeSinceLastPoint = (minutesSince / 1440) + " days ago";
            }
        }

        // Calculate accuracy statistics
        Double avgAccuracy = null;
        Integer goodAccuracyPercentage = null;
        if (!allPoints.isEmpty()) {
            List<Double> accuracies = allPoints.stream()
                .map(RawLocationPoint::getAccuracyMeters)
                .filter(Objects::nonNull)
                .toList();
            
            if (!accuracies.isEmpty()) {
                avgAccuracy = accuracies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                long goodAccuracyCount = accuracies.stream().filter(acc -> acc < 50.0).count();
                goodAccuracyPercentage = (int) ((goodAccuracyCount * 100) / accuracies.size());
            }
        }

        // Calculate average interval between points and check for fluctuation
        String avgInterval = null;
        long avgIntervalSeconds = -1;
        boolean hasFluctuatingFrequency = false;
        
        if (allPoints.size() > 1) {
            List<Long> intervals = new ArrayList<>();
            long totalIntervalSeconds = 0;
            
            for (int i = 1; i < allPoints.size(); i++) {
                long intervalSeconds = java.time.Duration.between(
                    allPoints.get(i-1).getTimestamp(), 
                    allPoints.get(i).getTimestamp()
                ).getSeconds();
                intervals.add(intervalSeconds);
                totalIntervalSeconds += intervalSeconds;
            }
            
            avgIntervalSeconds = totalIntervalSeconds / intervals.size();
            
            if (avgIntervalSeconds < 60) {
                avgInterval = avgIntervalSeconds + " seconds";
            } else if (avgIntervalSeconds < 3600) {
                avgInterval = (avgIntervalSeconds / 60) + " minutes";
            } else {
                avgInterval = (avgIntervalSeconds / 3600) + " hours";
            }
        }
        
        // Check for frequency fluctuation using only last 24h data
        if (last24hPoints.size() > 2) {
            List<Long> last24hIntervals = new ArrayList<>();
            long totalLast24hIntervalSeconds = 0;
            
            for (int i = 1; i < last24hPoints.size(); i++) {
                long intervalSeconds = java.time.Duration.between(
                    last24hPoints.get(i-1).getTimestamp(), 
                    last24hPoints.get(i).getTimestamp()
                ).getSeconds();
                last24hIntervals.add(intervalSeconds);
                totalLast24hIntervalSeconds += intervalSeconds;
            }
            
            if (!last24hIntervals.isEmpty()) {
                long avgLast24hIntervalSeconds = totalLast24hIntervalSeconds / last24hIntervals.size();
                
                // Check for frequency fluctuation (coefficient of variation > 1.0)
                if (last24hIntervals.size() > 2) {
                    long finalAvgLast24hIntervalSeconds = avgLast24hIntervalSeconds;
                    double variance = last24hIntervals.stream()
                        .mapToDouble(interval -> Math.pow(interval - finalAvgLast24hIntervalSeconds, 2))
                        .average().orElse(0.0);
                    double stdDev = Math.sqrt(variance);
                    double coefficientOfVariation = avgLast24hIntervalSeconds > 0 ? stdDev / avgLast24hIntervalSeconds : 0;
                    hasFluctuatingFrequency = coefficientOfVariation > 1.0;
                }
            }
        }

        // Determine status flags
        boolean isActivelyTracking = pointsLast24h > 0;
        boolean hasGoodFrequency = avgIntervalSeconds < 50;

        // Generate recommendations
        List<String> recommendations = new ArrayList<>();
        if (!isActivelyTracking) {
            recommendations.add(getMessage("integrations.data.quality.recommendation.no.data"));
        }
        if (avgIntervalSeconds > 50) {
            recommendations.add(getMessage("integrations.data.quality.recommendation.low.frequency"));
        }
        if (goodAccuracyPercentage != null && goodAccuracyPercentage < 70) {
            recommendations.add(getMessage("integrations.data.quality.recommendation.poor.accuracy"));
        }
        if (avgAccuracy != null && avgAccuracy > 100) {
            recommendations.add(getMessage("integrations.data.quality.recommendation.very.poor.accuracy"));
        }
        if (hasFluctuatingFrequency) {
            recommendations.add(getMessage("integrations.data.quality.recommendation.fluctuating.frequency"));
        }

        return new DataQualityReport(
            totalPoints, pointsLast24h, pointsLast7d, avgPointsPerDay,
            latestPointTime, timeSinceLastPoint,
            avgAccuracy, goodAccuracyPercentage, avgInterval,
            isActivelyTracking, hasGoodFrequency, hasFluctuatingFrequency, recommendations
        );
    }

    // Data class for the quality report
    public static class DataQualityReport {
        private final long totalPoints;
        private final int pointsLast24h;
        private final int pointsLast7d;
        private final int avgPointsPerDay;
        private final String latestPointTime;
        private final String timeSinceLastPoint;
        private final Double avgAccuracy;
        private final Integer goodAccuracyPercentage;
        private final String avgInterval;
        private final boolean isActivelyTracking;
        private final boolean hasGoodFrequency;
        private final boolean hasFluctuatingFrequency;
        private final List<String> recommendations;

        public DataQualityReport(long totalPoints, int pointsLast24h, int pointsLast7d, int avgPointsPerDay,
                               String latestPointTime, String timeSinceLastPoint, Double avgAccuracy,
                               Integer goodAccuracyPercentage, String avgInterval, boolean isActivelyTracking,
                               boolean hasGoodFrequency, boolean hasFluctuatingFrequency, List<String> recommendations) {
            this.totalPoints = totalPoints;
            this.pointsLast24h = pointsLast24h;
            this.pointsLast7d = pointsLast7d;
            this.avgPointsPerDay = avgPointsPerDay;
            this.latestPointTime = latestPointTime;
            this.timeSinceLastPoint = timeSinceLastPoint;
            this.avgAccuracy = avgAccuracy;
            this.goodAccuracyPercentage = goodAccuracyPercentage;
            this.avgInterval = avgInterval;
            this.isActivelyTracking = isActivelyTracking;
            this.hasGoodFrequency = hasGoodFrequency;
            this.hasFluctuatingFrequency = hasFluctuatingFrequency;
            this.recommendations = recommendations;
        }

        // Getters
        public long getTotalPoints() { return totalPoints; }
        public int getPointsLast24h() { return pointsLast24h; }
        public int getPointsLast7d() { return pointsLast7d; }
        public int getAvgPointsPerDay() { return avgPointsPerDay; }
        public String getLatestPointTime() { return latestPointTime; }
        public String getTimeSinceLastPoint() { return timeSinceLastPoint; }
        public Double getAvgAccuracy() { return avgAccuracy; }
        public Integer getGoodAccuracyPercentage() { return goodAccuracyPercentage; }
        public String getAvgInterval() { return avgInterval; }
        public boolean isActivelyTracking() { return isActivelyTracking; }
        public boolean isHasGoodFrequency() { return hasGoodFrequency; }
        public boolean isHasFluctuatingFrequency() { return hasFluctuatingFrequency; }
        public List<String> getRecommendations() { return recommendations; }
    }

    private void getExportDataContent(@AuthenticationPrincipal User user, Model model) {
        // Set default date range to today
        java.time.LocalDate today = java.time.LocalDate.now();
        model.addAttribute("startDate", today);
        model.addAttribute("endDate", today);
        
        // Get raw location points for today by default
        List<RawLocationPoint> rawLocationPoints = rawLocationPointJdbcService.findByUserAndDateRange(
            user, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        model.addAttribute("rawLocationPoints", rawLocationPoints);
    }

    private String getUserManagementPage(@AuthenticationPrincipal User user, Model model) {
        if (user.getRole() != Role.ADMIN) {
            // For non-admin users, show their own form
            model.addAttribute("userId", user.getId());
            model.addAttribute("username", user.getUsername());
            model.addAttribute("displayName", user.getDisplayName());
            model.addAttribute("selectedRole", user.getRole());
            model.addAttribute("externallyManaged", user.getExternalId() != null && oidcEnabled);
            model.addAttribute("externalProfile", user.getProfileUrl());
            model.addAttribute("localLoginDisabled", this.localLoginDisabled);
            UserSettings userSettings = userSettingsJdbcService.findByUserId(user.getId()).orElse(UserSettings.defaultSettings(user.getId()));
            model.addAttribute("selectedLanguage", userSettings.getSelectedLanguage());
            model.addAttribute("selectedUnitSystem", userSettings.getUnitSystem().name());
            model.addAttribute("preferColoredMap", userSettings.isPreferColoredMap());
            model.addAttribute("homeLatitude", userSettings.getHomeLatitude());
            model.addAttribute("homeLongitude", userSettings.getHomeLongitude());
            model.addAttribute("unitSystems", UnitSystem.values());
            model.addAttribute("isAdmin", false);
            model.addAttribute("timeZoneOverride", userSettings.getTimeZoneOverride());
            model.addAttribute("timeDisplayMode", userSettings.getTimeDisplayMode().name());
            model.addAttribute("availableTimezones", ZoneId.getAvailableZoneIds().stream().sorted());
            model.addAttribute("availableTimeDisplayModes", TimeDisplayMode.values());

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
