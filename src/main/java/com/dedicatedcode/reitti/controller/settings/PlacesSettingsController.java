package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.dto.PlaceInfo;
import com.dedicatedcode.reitti.event.SignificantPlaceCreatedEvent;
import com.dedicatedcode.reitti.model.AvailableCountry;
import com.dedicatedcode.reitti.model.Page;
import com.dedicatedcode.reitti.model.PageRequest;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geocoding.GeocodingResponse;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.GeocodingResponseJdbcService;
import com.dedicatedcode.reitti.repository.SignificantPlaceJdbcService;
import com.dedicatedcode.reitti.repository.SignificantPlaceOverrideJdbcService;
import com.dedicatedcode.reitti.service.PlaceService;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
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
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/settings/places")
public class PlacesSettingsController {
    private final PlaceService placeService;
    private final SignificantPlaceJdbcService placeJdbcService;
    private final SignificantPlaceOverrideJdbcService significantPlaceOverrideJdbcService;
    private final GeocodingResponseJdbcService geocodingResponseJdbcService;
    private final RabbitTemplate rabbitTemplate;
    private final GeometryFactory geometryFactory;
    private final MessageSource messageSource;
    private final boolean dataManagementEnabled;

    public PlacesSettingsController(PlaceService placeService,
                                    SignificantPlaceJdbcService placeJdbcService,
                                    SignificantPlaceOverrideJdbcService significantPlaceOverrideJdbcService,
                                    GeocodingResponseJdbcService geocodingResponseJdbcService,
                                    RabbitTemplate rabbitTemplate,
                                    GeometryFactory geometryFactory,
                                    MessageSource messageSource,
                                    @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled) {
        this.placeService = placeService;
        this.placeJdbcService = placeJdbcService;
        this.significantPlaceOverrideJdbcService = significantPlaceOverrideJdbcService;
        this.geocodingResponseJdbcService = geocodingResponseJdbcService;
        this.rabbitTemplate = rabbitTemplate;
        this.geometryFactory = geometryFactory;
        this.messageSource = messageSource;
        this.dataManagementEnabled = dataManagementEnabled;
    }

    @GetMapping
    public String getPage(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("activeSection", "places");
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);

        getPlacesContent(user, 0, "", model);
        return "settings/places";
    }

    @GetMapping("/places-content")
    public String getPlacesContent(@AuthenticationPrincipal User user,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "") String search,
                                   Model model) {
        Page<SignificantPlace> placesPage = placeService.getPlacesForUser(user, PageRequest.of(page, 20), search);

        // Convert to PlaceInfo objects
        List<PlaceInfo> places = placesPage.getContent().stream()
                .map(PlacesSettingsController::convertToPlaceInfo)
                .collect(Collectors.toList());

        model.addAttribute("currentPage", placesPage.getNumber());
        model.addAttribute("totalPages", placesPage.getTotalPages());
        model.addAttribute("places", places);
        model.addAttribute("isEmpty", places.isEmpty());
        model.addAttribute("placeTypes", SignificantPlace.PlaceType.values());
        model.addAttribute("search", search);

        return "settings/places :: places-content";
    }

    @GetMapping("/{placeId}/edit")
    public String editPlace(@PathVariable Long placeId,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "") String search,
                            Authentication authentication,
                            Model model) {

        User user = (User) authentication.getPrincipal();
        if (!this.placeJdbcService.exists(user, placeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        try {
            SignificantPlace place = placeJdbcService.findById(placeId).orElseThrow();

            PlaceInfo placeInfo = convertToPlaceInfo(place);

            // Get visit statistics for this place
            var visitStats = placeService.getVisitStatisticsForPlace(user, placeId);

            model.addAttribute("place", placeInfo);
            model.addAttribute("currentPage", page);
            model.addAttribute("search", search);
            model.addAttribute("placeTypes", SignificantPlace.PlaceType.values());
            model.addAttribute("visitStats", visitStats);
            model.addAttribute("hasPolygon", place.getPolygon() != null && !place.getPolygon().isEmpty());

        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("message.error.place.update", e.getMessage()));
            return getPlacesContent(user, page, search, model);
        }

        return "fragments/places :: edit-place-content";
    }

    @PostMapping("/{placeId}/update")
    public String updatePlace(@PathVariable Long placeId,
                              @RequestParam String name,
                              @RequestParam(required = false) String address,
                              @RequestParam(required = false) String city,
                              @RequestParam(required = false) String countryCode,
                              @RequestParam(required = false) String type,
                              @RequestParam(required = false) String polygonData,
                              @RequestParam(required = false) String returnUrl,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "") String search,
                              Authentication authentication,
                              Model model) {

        User user = (User) authentication.getPrincipal();
        if (this.placeJdbcService.exists(user, placeId)) {
            try {
                SignificantPlace significantPlace = placeJdbcService.findById(placeId).orElseThrow();
                SignificantPlace updatedPlace = significantPlace.withName(name);
                
                if (address != null) {
                    updatedPlace = updatedPlace.withAddress(address.trim().isEmpty() ? null : address.trim());
                }
                
                if (city != null) {
                    updatedPlace = updatedPlace.withCity(city.trim().isEmpty() ? null : city.trim());
                }
                
                if (countryCode != null) {
                    updatedPlace = updatedPlace.withCountryCode(countryCode.trim().isEmpty() ? null : countryCode.trim());
                }

                if (type != null && !type.isEmpty()) {
                    try {
                        SignificantPlace.PlaceType placeType = SignificantPlace.PlaceType.valueOf(type);
                        updatedPlace = updatedPlace.withType(placeType);
                    } catch (IllegalArgumentException e) {
                        model.addAttribute("errorMessage", getMessage("message.error.place.update", "Invalid place type"));
                        if (returnUrl != null) {
                            return editPolygon(placeId, returnUrl, authentication, model);
                        }
                        return editPlace(placeId, page, search, authentication, model);
                    }
                }

                // TODO: Parse polygonData and update polygon
                // For now, we'll just update the basic fields

                placeJdbcService.update(updatedPlace);
                significantPlaceOverrideJdbcService.insertOverride(user, updatedPlace);
                
                if (returnUrl != null) {
                    return "redirect:" + returnUrl;
                }
                
                model.addAttribute("successMessage", getMessage("message.success.place.updated"));
                return editPlace(placeId, page, search, authentication, model);
            } catch (Exception e) {
                model.addAttribute("errorMessage", getMessage("message.error.place.update", e.getMessage()));
                if (returnUrl != null) {
                    return editPolygon(placeId, returnUrl, authentication, model);
                }
                return editPlace(placeId, page, search, authentication, model);
            }
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    @PostMapping("/{placeId}/geocode")
    public String geocodePlace(@PathVariable Long placeId,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "") String search,
                               Authentication authentication,
                               Model model) {

        User user = (User) authentication.getPrincipal();
        if (this.placeJdbcService.exists(user, placeId)) {
            try {
                SignificantPlace significantPlace = placeJdbcService.findById(placeId).orElseThrow();

                // Clear geocoding data and mark as not geocoded
                SignificantPlace clearedPlace = significantPlace.withGeocoded(false).withAddress(null);
                placeJdbcService.update(clearedPlace);
                significantPlaceOverrideJdbcService.clear(user, clearedPlace);
                // Send SignificantPlaceCreatedEvent to trigger geocoding
                SignificantPlaceCreatedEvent event = new SignificantPlaceCreatedEvent(
                        user.getUsername(),
                        null,
                        significantPlace.getId(),
                        significantPlace.getLatitudeCentroid(),
                        significantPlace.getLongitudeCentroid(),
                        UUID.randomUUID().toString()
                );
                rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.SIGNIFICANT_PLACE_ROUTING_KEY, event);

                model.addAttribute("successMessage", getMessage("places.geocode.success"));
            } catch (Exception e) {
                model.addAttribute("errorMessage", getMessage("places.geocode.error", e.getMessage()));
            }
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        return getPlacesContent(user, page, search, model);
    }


    @GetMapping("/{placeId}/geocoding-response")
    public String getGeocodingResponse(@PathVariable Long placeId,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "") String search,
                                       @RequestParam(defaultValue = "places") String context,
                                       Authentication authentication,
                                       Model model) {

        User user = (User) authentication.getPrincipal();
        if (!this.placeJdbcService.exists(user, placeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        try {
            SignificantPlace place = placeJdbcService.findById(placeId).orElseThrow();

            PlaceInfo placeInfo = convertToPlaceInfo(place);

            // Get all geocoding responses for this place
            List<GeocodingResponse> geocodingResponses = geocodingResponseJdbcService.findBySignificantPlace(place);

            model.addAttribute("place", placeInfo);
            model.addAttribute("currentPage", page);
            model.addAttribute("search", search);
            model.addAttribute("context", context);
            model.addAttribute("geocodingResponses", geocodingResponses);

        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("message.error.place.update", e.getMessage()));
            return getPlacesContent(user, page, search, model);
        }

        return "fragments/places :: geocoding-response-content";
    }

    @GetMapping("/{placeId}/edit-polygon")
    public String editPolygon(@PathVariable Long placeId,
                              @RequestParam(required = false) String returnUrl,
                              Authentication authentication,
                              Model model) {

        User user = (User) authentication.getPrincipal();
        if (!this.placeJdbcService.exists(user, placeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        try {
            SignificantPlace place = placeJdbcService.findById(placeId).orElseThrow();

            PlaceInfo placeInfo = convertToPlaceInfo(place);

            model.addAttribute("place", placeInfo);
            model.addAttribute("placeTypes", SignificantPlace.PlaceType.values());
            model.addAttribute("returnUrl", returnUrl != null ? returnUrl : "/settings/places");

            Point point = geometryFactory.createPoint(new Coordinate(place.getLongitudeCentroid(), place.getLongitudeCentroid()));

            List<PlaceInfo> nearbyPlaces = this.placeJdbcService.findNearbyPlaces(user.getId(), point, 1000.0).stream().map(PlacesSettingsController::convertToPlaceInfo).toList();
            model.addAttribute("availableCountries", AvailableCountry.values());
            model.addAttribute("nearbyPlaces", nearbyPlaces);

        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("message.error.place.update", e.getMessage()));
            return "redirect:/settings/places";
        }

        return "settings/edit-polygon";
    }

    private static PlaceInfo convertToPlaceInfo(SignificantPlace place) {
        return new PlaceInfo(
                place.getId(),
                place.getName(),
                place.getAddress(),
                place.getCity(),
                place.getCountryCode(),
                place.getLatitudeCentroid(),
                place.getLongitudeCentroid(),
                place.getType(),
                place.getPolygon()
        );
    }


    @PostMapping("/{placeId}/remove-polygon")
    public String removePolygon(@PathVariable Long placeId,
                                @RequestParam(required = false) String returnUrl,
                                Authentication authentication) {

        User user = (User) authentication.getPrincipal();
        if (!this.placeJdbcService.exists(user, placeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        try {
            SignificantPlace significantPlace = placeJdbcService.findById(placeId).orElseThrow();
            SignificantPlace updatedPlace = significantPlace.withPolygon(null);
            
            placeJdbcService.update(updatedPlace);
            significantPlaceOverrideJdbcService.insertOverride(user, updatedPlace);
            
        } catch (Exception e) {
            // Log error but continue to redirect
        }

        return "redirect:" + (returnUrl != null ? returnUrl : "/settings/places");
    }


    private String getMessage(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
