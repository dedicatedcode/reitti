package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.dto.PlaceInfo;
import com.dedicatedcode.reitti.event.SignificantPlaceCreatedEvent;
import com.dedicatedcode.reitti.model.Page;
import com.dedicatedcode.reitti.model.PageRequest;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geocoding.GeocodingResponse;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.GeocodingResponseJdbcService;
import com.dedicatedcode.reitti.repository.SignificantPlaceJdbcService;
import com.dedicatedcode.reitti.service.PlaceService;
import com.dedicatedcode.reitti.service.QueueStatsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;


@Controller
@RequestMapping("/settings")
public class SettingsController {
    private final QueueStatsService queueStatsService;
    private final PlaceService placeService;
    private final SignificantPlaceJdbcService placeJdbcService;
    private final GeocodingResponseJdbcService geocodingResponseJdbcService;

    private final RabbitTemplate rabbitTemplate;
    private final boolean dataManagementEnabled;
    private final MessageSource messageSource;

    public SettingsController(QueueStatsService queueStatsService,
                              PlaceService placeService, SignificantPlaceJdbcService placeJdbcService,
                              GeocodingResponseJdbcService geocodingResponseJdbcService,
                              RabbitTemplate rabbitTemplate,
                              @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled,
                              MessageSource messageSource) {
        this.queueStatsService = queueStatsService;
        this.placeService = placeService;
        this.placeJdbcService = placeJdbcService;
        this.geocodingResponseJdbcService = geocodingResponseJdbcService;
        this.rabbitTemplate = rabbitTemplate;
        this.dataManagementEnabled = dataManagementEnabled;
        this.messageSource = messageSource;
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

            case "places-management":
                getPlacesContent(user, 0, model);
                break;
        }
        
        return "settings";
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

        return "fragments/places :: places-content";
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
        
        return "fragments/places :: geocoding-response-content";
    }


    @GetMapping("/job-status")
    public String getJobStatus(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("activeSection", "job-status");
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        model.addAttribute("queueStats", queueStatsService.getQueueStats());
        return "settings/job-status";
    }

    @GetMapping("/queue-stats-content")
    public String getQueueStatsContent(Model model) {
        model.addAttribute("queueStats", queueStatsService.getQueueStats());
        return "settings/job-status :: queue-stats-content";
    }
}
