package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.dto.PlaceInfo;
import com.dedicatedcode.reitti.dto.SuppressedVisitInfo;
import com.dedicatedcode.reitti.model.*;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.GeoUtils;
import com.dedicatedcode.reitti.model.geo.NoVisitZone;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.SuppressedVisit;
import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.model.geocoding.GeocodingResponse;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.GeocodingResponseJdbcService;
import com.dedicatedcode.reitti.repository.NoVisitZoneJdbcService;
import com.dedicatedcode.reitti.repository.SignificantPlaceJdbcService;
import com.dedicatedcode.reitti.repository.SignificantPlaceOverrideJdbcService;
import com.dedicatedcode.reitti.repository.SuppressedVisitJdbcService;
import com.dedicatedcode.reitti.service.DataCleanupService;
import com.dedicatedcode.reitti.service.I18nService;
import com.dedicatedcode.reitti.service.NoVisitZoneService;
import com.dedicatedcode.reitti.service.PlaceChangeDetectionService;
import com.dedicatedcode.reitti.service.PlaceService;
import com.dedicatedcode.reitti.service.SuppressedVisitService;
import com.dedicatedcode.reitti.service.TimeUtil;
import com.dedicatedcode.reitti.service.geocoding.GeocodeResult;
import com.dedicatedcode.reitti.service.geocoding.GeocodeServiceManager;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.jobs.JobType;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/settings/places")
public class PlacesSettingsController {
    private static final Logger log = LoggerFactory.getLogger(PlacesSettingsController.class);
    private final PlaceService placeService;
    private final SignificantPlaceJdbcService placeJdbcService;
    private final SignificantPlaceOverrideJdbcService significantPlaceOverrideJdbcService;
    private final GeocodingResponseJdbcService geocodingResponseJdbcService;
    private final GeocodeServiceManager geocodeServiceManager;
    private final GeometryFactory geometryFactory;
    private final I18nService i18nService;
    private final PlaceChangeDetectionService placeChangeDetectionService;
    private final JobSchedulingService jobSchedulingService;
    private final JobDetail locationDataCleanupTask;
    private final boolean dataManagementEnabled;
    private final ObjectMapper objectMapper;
    private final SuppressedVisitJdbcService suppressedVisitJdbcService;
    private final SuppressedVisitService suppressedVisitService;
    private final NoVisitZoneJdbcService noVisitZoneJdbcService;
    private final NoVisitZoneService noVisitZoneService;

    public PlacesSettingsController(PlaceService placeService,
                                    SignificantPlaceJdbcService placeJdbcService,
                                    SignificantPlaceOverrideJdbcService significantPlaceOverrideJdbcService,
                                    GeocodingResponseJdbcService geocodingResponseJdbcService,
                                    GeocodeServiceManager geocodeServiceManager,
                                    GeometryFactory geometryFactory,
                                    I18nService i18nService,
                                    PlaceChangeDetectionService placeChangeDetectionService, JobSchedulingService jobSchedulingService,
                                    @Qualifier("polygonUpdateJob") JobDetail locationDataCleanupTask,
                                    @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled,
                                    ObjectMapper objectMapper,
                                    SuppressedVisitJdbcService suppressedVisitJdbcService,
                                    SuppressedVisitService suppressedVisitService,
                                    NoVisitZoneJdbcService noVisitZoneJdbcService,
                                    NoVisitZoneService noVisitZoneService) {
        this.placeService = placeService;
        this.placeJdbcService = placeJdbcService;
        this.significantPlaceOverrideJdbcService = significantPlaceOverrideJdbcService;
        this.geocodingResponseJdbcService = geocodingResponseJdbcService;
        this.geocodeServiceManager = geocodeServiceManager;
        this.geometryFactory = geometryFactory;
        this.i18nService = i18nService;
        this.placeChangeDetectionService = placeChangeDetectionService;
        this.jobSchedulingService = jobSchedulingService;
        this.locationDataCleanupTask = locationDataCleanupTask;
        this.dataManagementEnabled = dataManagementEnabled;
        this.objectMapper = objectMapper;
        this.suppressedVisitJdbcService = suppressedVisitJdbcService;
        this.suppressedVisitService = suppressedVisitService;
        this.noVisitZoneJdbcService = noVisitZoneJdbcService;
        this.noVisitZoneService = noVisitZoneService;
    }

    @GetMapping
    public String getPage(@AuthenticationPrincipal User user,
                          Model model,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "") String search) {
        if (user.getUserType() == UserType.LIVE_DATA_ONLY) {
            model.addAttribute("activeSection", "places");
            model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
            model.addAttribute("dataManagementEnabled", dataManagementEnabled);
            return "settings/unavailable";
        }
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

    @GetMapping("/{placeId}/edit-form")
    public String getEditForm(@PathVariable Long placeId,
                              @RequestParam(required = false) String returnUrl,
                              Authentication authentication,
                              Model model) {
        User user = (User) authentication.getPrincipal();
        if (!this.placeJdbcService.exists(user, placeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        SignificantPlace place = placeJdbcService.findById(placeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("place", convertToPlaceInfo(place));
        model.addAttribute("placeTypes", SignificantPlace.PlaceType.values());
        model.addAttribute("availableCountries", AvailableCountry.values());
        model.addAttribute("returnUrl", returnUrl);
        return "settings/edit-place :: edit-form";
    }

    @GetMapping("/search-fragment")
    public String searchPlacesFragment(@AuthenticationPrincipal User user,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "") String search,
                                       Model model) {
        Page<SignificantPlace> placesPage = placeService.getPlacesForUser(user, PageRequest.of(page, 10), search);
        List<PlaceInfo> places = placesPage.getContent().stream()
                .map(PlacesSettingsController::convertToPlaceInfo)
                .toList();
        model.addAttribute("places", places);
        model.addAttribute("search", search);
        model.addAttribute("currentPage", placesPage.getNumber());
        model.addAttribute("isLast", placesPage.getNumber() >= placesPage.getTotalPages() - 1);
        return "fragments/place-search :: search-results";
    }

    @GetMapping("/suppressed-fragment")
    public String getSuppressedFragment(@AuthenticationPrincipal User user,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "UTC") String timezone,
                                        Model model) {
        populateSuppressedModel(user, page, timezone, model);
        return "fragments/suppressed-visits :: suppressed-list";
    }

    @GetMapping("/suppressed-locations")
    @ResponseBody
    public List<SuppressedVisitInfo> getSuppressedLocations(@AuthenticationPrincipal User user) {
        return suppressedVisitJdbcService.findAllInfosByUser(user);
    }

    @GetMapping("/zones-fragment")
    public String getZonesFragment(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("zones", noVisitZoneJdbcService.findByUser(user));
        return "fragments/no-visit-zones :: zones-list";
    }

    @GetMapping("/zones-locations")
    @ResponseBody
    public List<NoVisitZone> getZoneLocations(@AuthenticationPrincipal User user) {
        return noVisitZoneJdbcService.findByUser(user);
    }

    @PostMapping("/zones")
    public String createZone(@AuthenticationPrincipal User user,
                             @RequestParam String name,
                             @RequestParam String polygonData,
                             Model model) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zone name must not be empty");
        }
        List<GeoPoint> polygon;
        try {
            polygon = parsePolygonData(polygonData);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        noVisitZoneService.create(user, new NoVisitZone(name.trim(), polygon));
        model.addAttribute("zones", noVisitZoneJdbcService.findByUser(user));
        return "fragments/no-visit-zones :: zones-list";
    }

    @PostMapping("/zones/{id}/update")
    public String updateZone(@AuthenticationPrincipal User user,
                             @PathVariable Long id,
                             @RequestParam String polygonData,
                             Model model) {
        List<GeoPoint> polygon;
        try {
            polygon = parsePolygonData(polygonData);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        noVisitZoneService.updateGeometry(user, id, polygon);
        model.addAttribute("zones", noVisitZoneJdbcService.findByUser(user));
        return "fragments/no-visit-zones :: zones-list";
    }

    @PostMapping("/zones/{id}/delete")
    public String deleteZone(@AuthenticationPrincipal User user,
                             @PathVariable Long id,
                             Model model) {
        NoVisitZone zone = noVisitZoneJdbcService.findById(user, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        noVisitZoneService.delete(user, zone);
        model.addAttribute("zones", noVisitZoneJdbcService.findByUser(user));
        return "fragments/no-visit-zones :: zones-list";
    }

    @PostMapping("/suppressed/{id}/restore")
    public String restoreSuppressedVisit(@AuthenticationPrincipal User user,
                                         @PathVariable Long id,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "UTC") String timezone,
                                         Model model) {
        SuppressedVisit suppressedVisit = suppressedVisitJdbcService.findById(user, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        suppressedVisitService.restore(user, suppressedVisit);
        populateSuppressedModel(user, page, timezone, model);
        return "fragments/suppressed-visits :: suppressed-list";
    }

    private void populateSuppressedModel(User user, int page, String timezoneParam, Model model) {
        ZoneId timezone = parseTimezone(timezoneParam);
        Page<SuppressedVisitInfo> suppressedPage = suppressedVisitJdbcService.findInfosByUser(user, PageRequest.of(Math.max(0, page), 10));
        List<SuppressedVisitView> items = suppressedPage.getContent().stream()
                .map(info -> new SuppressedVisitView(
                        info.id(),
                        info.placeId(),
                        info.placeName(),
                        info.latitudeCentroid(),
                        info.longitudeCentroid(),
                        TimeUtil.adjustInstant(info.startTime(), timezone),
                        TimeUtil.adjustInstant(info.endTime(), timezone)))
                .toList();
        model.addAttribute("items", items);
        model.addAttribute("currentPage", suppressedPage.getNumber());
        model.addAttribute("totalPages", suppressedPage.getTotalPages());
        model.addAttribute("timezone", timezone.getId());
    }

    private ZoneId parseTimezone(String timezoneParam) {
        try {
            return ZoneId.of(timezoneParam);
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }

    @PostMapping("/{placeId}/check-update")
    @ResponseBody
    public CheckUpdateResponse checkUpdate(@PathVariable Long placeId,
                                           @RequestParam(required = false) String polygonData,
                                          Authentication authentication) {

        User user = (User) authentication.getPrincipal();
        if (!this.placeJdbcService.exists(user, placeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        PlaceChangeDetectionService.PlaceChangeAnalysis analysis = 
            placeChangeDetectionService.analyzeChanges(user, placeId, polygonData);
        
        return new CheckUpdateResponse(analysis.canProceed(), analysis.warnings());
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
                        GeoPoint centroid = GeoUtils.calculatePolygonCentroid(polygon);
                        updatedPlace = updatedPlace.withLatitudeCentroid(centroid.latitude())
                                                   .withLongitudeCentroid(centroid.longitude());
                    } catch (Exception e) {
                        model.addAttribute("errorMessage", i18nService.translate("message.error.place.update", "Invalid polygon data: " + e.getMessage()));
                        return editPolygon(placeId, returnUrl, authentication, model);
                    }
                } else {
                    updatedPlace = updatedPlace.withPolygon(null);
                }

                if (!this.placeChangeDetectionService.analyzeChanges(user, placeId, polygonData).canProceed()) {
                    placeJdbcService.update(updatedPlace);
                    log.info("Significant change detected for place [{}]. Will issue a recalculation of all affected dates", significantPlace);
                    this.jobSchedulingService.enqueueTask(locationDataCleanupTask, new DataCleanupService.TaskData(user, updatedPlace),
                                                          JobSchedulingService.Metadata.builder()
                                                                  .user(user)
                                                                  .friendlyName("Update Polygon of " + significantPlace.getName())
                                                                  .jobType(JobType.DATA_RECALCULATION)
                                                                  .build());

                } else {
                    placeJdbcService.update(updatedPlace);
                }
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
                               Model model) {

        User user = (User) authentication.getPrincipal();
        if (!this.placeJdbcService.exists(user, placeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        try {
            SignificantPlace significantPlace = placeJdbcService.findById(placeId).orElseThrow();
            Map<GeocoderType, List<GeocodeResult>> results = geocodeServiceManager.reverseGeocodeAll(significantPlace);

            model.addAttribute("place", convertToPlaceInfo(significantPlace));
            model.addAttribute("geocodingResults", results);
            model.addAttribute("returnUrl", returnUrl);
            model.addAttribute("page", page);
            model.addAttribute("search", search);

            return "settings/geocode-results :: content";
        } catch (Exception e) {
            model.addAttribute("errorMessage", i18nService.translate("places.geocode.error", e.getMessage()));
            return "redirect:/settings/places";
        }
    }

    @PostMapping("/{placeId}/apply-geocode-result")
    public String applyGeocodeResult(@PathVariable Long placeId,
                                     @RequestParam String name,
                                     @RequestParam(required = false) String address,
                                     @RequestParam(required = false) String city,
                                     @RequestParam(required = false) String countryCode,
                                     @RequestParam(required = false) String returnUrl,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "") String search,
                                     Authentication authentication,
                                     RedirectAttributes redirectAttributes) {
        User user = (User) authentication.getPrincipal();
        if (!this.placeJdbcService.exists(user, placeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        try {
            SignificantPlace place = placeJdbcService.findById(placeId).orElseThrow();
            SignificantPlace updated = place.withName(name)
                    .withAddress(address)
                    .withCity(city)
                    .withCountryCode(countryCode)
                    .withGeocoded(true);

            placeJdbcService.update(updated);
            significantPlaceOverrideJdbcService.clear(user, updated);
            redirectAttributes.addFlashAttribute("successMessage", i18nService.translate("places.geocode.success", new Object[]{}));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", i18nService.translate("places.geocode.error", e.getMessage()));
        }

        String redirectUrl = returnUrl != null ? returnUrl : "/settings/places?page=" + page + "&search=" + search;
        return "redirect:" + redirectUrl;
    }

    @GetMapping("/nearby")
    @ResponseBody
    public List<PlaceInfo> getNearbyPlaces(@AuthenticationPrincipal User user,
                                           @RequestParam double lat,
                                           @RequestParam double lng,
                                           @RequestParam(defaultValue = "5000") double radius) {
        double clampedRadius = Math.min(Math.max(radius, 500), 200_000);
        Point point = geometryFactory.createPoint(new Coordinate(lng, lat));
        return placeJdbcService.findNearbyPlaces(user.getId(), point, clampedRadius).stream()
                .map(PlacesSettingsController::convertToPlaceInfo)
                .sorted(Comparator.comparingDouble(p -> GeoUtils.distanceInMeters(lat, lng, p.lat(), p.lng())))
                .limit(100)
                .toList();
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

    @GetMapping("/editor")
    public String editor(@AuthenticationPrincipal User user, Model model) {
        if (user.getUserType() == UserType.LIVE_DATA_ONLY) {
            model.addAttribute("activeSection", "places");
            model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
            model.addAttribute("dataManagementEnabled", dataManagementEnabled);
            return "settings/unavailable";
        }
        model.addAttribute("nearbyPlaces", List.of());
        return "settings/edit-place";
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

            List<PlaceInfo> nearbyPlaces = this.placeJdbcService.findNearbyPlaces(user.getId(), point, 5000.0).stream().map(PlacesSettingsController::convertToPlaceInfo).toList();
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

    public record CheckUpdateResponse(boolean canProceed, List<String> warnings) {
    }

    public record SuppressedVisitView(long id, Long placeId, String placeName, double lat, double lng,
                                      LocalDateTime start, LocalDateTime end) {
    }

}
