package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.dto.PlaceInfo;
import com.dedicatedcode.reitti.event.SignificantPlaceCreatedEvent;
import com.dedicatedcode.reitti.model.AvailableCountry;
import com.dedicatedcode.reitti.model.Page;
import com.dedicatedcode.reitti.model.PageRequest;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geocoding.GeocodingResponse;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.GeocodingResponseJdbcService;
import com.dedicatedcode.reitti.repository.SignificantPlaceJdbcService;
import com.dedicatedcode.reitti.repository.SignificantPlaceOverrideJdbcService;
import com.dedicatedcode.reitti.service.I18nService;
import com.dedicatedcode.reitti.service.PlaceService;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
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
    private final I18nService i18nService;
    private final boolean dataManagementEnabled;
    private final ObjectMapper objectMapper;

    public PlacesSettingsController(PlaceService placeService,
                                    SignificantPlaceJdbcService placeJdbcService,
                                    SignificantPlaceOverrideJdbcService significantPlaceOverrideJdbcService,
                                    GeocodingResponseJdbcService geocodingResponseJdbcService,
                                    RabbitTemplate rabbitTemplate,
                                    GeometryFactory geometryFactory,
                                    I18nService i18nService,
                                    @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled,
                                    ObjectMapper objectMapper) {
        this.placeService = placeService;
        this.placeJdbcService = placeJdbcService;
        this.significantPlaceOverrideJdbcService = significantPlaceOverrideJdbcService;
        this.geocodingResponseJdbcService = geocodingResponseJdbcService;
        this.rabbitTemplate = rabbitTemplate;
        this.geometryFactory = geometryFactory;
        this.i18nService = i18nService;
        this.dataManagementEnabled = dataManagementEnabled;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public String getPage(@AuthenticationPrincipal User user,
                          Model model,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "") String search) {
        model.addAttribute("activeSection", "places");
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);

        getPlacesContent(user, page, search, model);
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
        model.addAttribute("returnUrl", "/settings/places?search=" + search + "&page=" + page);

        return "settings/places :: places-content";
    }

    @PostMapping("/{placeId}/check-update")
    @ResponseBody
    public CheckUpdateResponse checkUpdate(@PathVariable Long placeId,
                                          @RequestParam(required = false) String type,
                                          @RequestParam(required = false) String polygonData,
                                          Authentication authentication) {

        User user = (User) authentication.getPrincipal();
        if (!this.placeJdbcService.exists(user, placeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        try {
            SignificantPlace currentPlace = placeJdbcService.findById(placeId).orElseThrow();
            List<String> warnings = new ArrayList<>();

            boolean hadPolygon = currentPlace.getPolygon() != null && !currentPlace.getPolygon().isEmpty();
            boolean willHavePolygon = polygonData != null && !polygonData.trim().isEmpty();
            
            if (hadPolygon && !willHavePolygon) {
                warnings.add(i18nService.translate("places.warning.polygon.removal"));
            }

            if (!hadPolygon && willHavePolygon) {
                warnings.add(i18nService.translate("places.warning.polygon.addition"));
            }

            // Check if polygon is being significantly changed
            if (hadPolygon && willHavePolygon) {
                try {
                    List<GeoPoint> newPolygon = parsePolygonData(polygonData);
                    GeoPoint newCentroid = calculatePolygonCentroid(newPolygon);
                    GeoPoint currentCentroid = new GeoPoint(currentPlace.getLatitudeCentroid(), currentPlace.getLongitudeCentroid());
                    
                    // Calculate distance between centroids (rough approximation)
                    double latDiff = Math.abs(newCentroid.latitude() - currentCentroid.latitude());
                    double lngDiff = Math.abs(newCentroid.longitude() - currentCentroid.longitude());
                    
                    // If centroid moved significantly (more than ~10m at typical latitudes)
                    if (latDiff > 0.0001 || lngDiff > 0.0001) {
                        warnings.add(i18nService.translate("places.warning.polygon.significant_change"));
                    }
                } catch (Exception e) {
                    // If polygon parsing fails, we'll catch it in the actual update
                }
            }

            // Check for overlapping boundaries with existing visits
            if (willHavePolygon) {
                try {
                    List<GeoPoint> newPolygon = parsePolygonData(polygonData);
                    int overlappingVisits = checkForOverlappingPlaces(user, placeId, newPolygon);
                    if (overlappingVisits > 0) {
                        warnings.add(i18nService.translate("places.warning.overlapping.visits", overlappingVisits));
                    }
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to parse polygon data: " + e.getMessage(), e);
                }
            }

            return new CheckUpdateResponse(warnings.isEmpty(), warnings);

        } catch (Exception e) {
            return new CheckUpdateResponse(false, List.of(i18nService.translate("places.warning.general_error", e.getMessage())));
        }
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
                        model.addAttribute("errorMessage", i18nService.translate("message.error.place.update", "Invalid place type"));
                        return editPolygon(placeId, returnUrl, authentication, model);
                    }
                }

                // Parse polygon data if provided
                if (polygonData != null && !polygonData.trim().isEmpty()) {
                    try {
                        List<GeoPoint> polygon = parsePolygonData(polygonData);
                        updatedPlace = updatedPlace.withPolygon(polygon);
                        
                        // Calculate and update the centroid
                        GeoPoint centroid = calculatePolygonCentroid(polygon);
                        updatedPlace = updatedPlace.withLatitudeCentroid(centroid.latitude())
                                                   .withLongitudeCentroid(centroid.longitude());
                    } catch (Exception e) {
                        model.addAttribute("errorMessage", i18nService.translate("message.error.place.update", "Invalid polygon data: " + e.getMessage()));
                        return editPolygon(placeId, returnUrl, authentication, model);
                    }
                } else {
                    updatedPlace = updatedPlace.withPolygon(null);
                }

                placeJdbcService.update(updatedPlace);
                significantPlaceOverrideJdbcService.insertOverride(user, updatedPlace);
                
                return "redirect:" + returnUrl;
            } catch (Exception e) {
                model.addAttribute("errorMessage", i18nService.translate("message.error.place.update", e.getMessage()));
                return editPolygon(placeId, returnUrl, authentication, model);
            }
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    @PostMapping("/{placeId}/geocode")
    public String geocodePlace(@PathVariable Long placeId,
                               @RequestParam(required = false) String returnUrl,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "") String search,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {

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

                redirectAttributes.addFlashAttribute("successMessage", i18nService.translate("places.geocode.success", new Object[]{}));
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("errorMessage", i18nService.translate("places.geocode.error", e.getMessage()));
            }
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        // Redirect to returnUrl if provided, otherwise to places list
        String redirectUrl = returnUrl != null ? returnUrl : "/settings/places?page=" + page + "&search=" + search;
        return "redirect:" + redirectUrl;
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
            model.addAttribute("errorMessage", i18nService.translate("message.error.place.update", e.getMessage()));
            return getPlacesContent(user, page, search, model);
        }

        return "fragments/places :: geocoding-response-content";
    }

    @GetMapping("/{placeId}/edit")
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
            
            model.addAttribute("returnUrl", returnUrl);

            Point point = geometryFactory.createPoint(new Coordinate(place.getLongitudeCentroid(), place.getLatitudeCentroid()));

            List<PlaceInfo> nearbyPlaces = this.placeJdbcService.findNearbyPlaces(user.getId(), point, 0.019).stream().map(PlacesSettingsController::convertToPlaceInfo).toList();
            model.addAttribute("availableCountries", AvailableCountry.values());
            model.addAttribute("nearbyPlaces", nearbyPlaces);

        } catch (Exception e) {
            model.addAttribute("errorMessage", i18nService.translate("message.error.place.update", e.getMessage()));
            return "redirect:/settings/places";
        }

        return "settings/edit-place";
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

    private List<GeoPoint> parsePolygonData(String polygonData) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(polygonData);
        List<GeoPoint> geoPoints = new ArrayList<>();
        
        if (jsonNode.isArray()) {
            for (JsonNode pointNode : jsonNode) {
                if (pointNode.has("lat") && pointNode.has("lng")) {
                    double lat = pointNode.get("lat").asDouble();
                    double lng = pointNode.get("lng").asDouble();
                    geoPoints.add(new GeoPoint(lat, lng));
                } else {
                    throw new IllegalArgumentException("Each point must have 'lat' and 'lng' properties");
                }
            }
        } else {
            throw new IllegalArgumentException("Polygon data must be an array of coordinate objects");
        }
        
        if (geoPoints.size() < 3) {
            throw new IllegalArgumentException("Polygon must have at least 3 points");
        }
        
        return geoPoints;
    }

    private GeoPoint calculatePolygonCentroid(List<GeoPoint> polygon) {
        if (polygon == null || polygon.isEmpty()) {
            throw new IllegalArgumentException("Polygon cannot be null or empty");
        }
        
        // Remove duplicate points (especially the closing point that duplicates the first point)
        List<GeoPoint> uniquePoints = new ArrayList<>();
        for (GeoPoint point : polygon) {
            boolean isDuplicate = uniquePoints.stream().anyMatch(existing ->
                Math.abs(existing.latitude() - point.latitude()) < 0.000001 &&
                Math.abs(existing.longitude() - point.longitude()) < 0.000001
            );
            if (!isDuplicate) {
                uniquePoints.add(point);
            }
        }
        
        // Calculate centroid as the arithmetic mean of unique vertices
        double avgLat = uniquePoints.stream().mapToDouble(GeoPoint::latitude).average().orElse(0.0);
        double avgLng = uniquePoints.stream().mapToDouble(GeoPoint::longitude).average().orElse(0.0);
        
        return new GeoPoint(avgLat, avgLng);
    }

    private int checkForOverlappingPlaces(User user, Long placeId, List<GeoPoint> newPolygon) {
        try {
            // Find places that would overlap with the new polygon
            List<SignificantPlace> overlappingPlaces = placeJdbcService.findPlacesOverlappingWithPolygon(
                user.getId(), placeId, newPolygon);
            
            return overlappingPlaces.size();
        } catch (Exception e) {
            // Log error but don't fail the check
            System.err.println("Error checking for overlapping places: " + e.getMessage());
            return 0;
        }
    }

    public static class CheckUpdateResponse {
        private final boolean canProceed;
        private final List<String> warnings;

        public CheckUpdateResponse(boolean canProceed, List<String> warnings) {
            this.canProceed = canProceed;
            this.warnings = warnings;
        }

        public boolean isCanProceed() {
            return canProceed;
        }

        public List<String> getWarnings() {
            return warnings;
        }
    }

}
