package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.event.SignificantPlaceCreatedEvent;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.GeocodeServiceJdbcService;
import com.dedicatedcode.reitti.repository.SignificantPlaceJdbcService;
import com.dedicatedcode.reitti.repository.SignificantPlaceOverrideJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.I18nService;
import com.dedicatedcode.reitti.service.geocoding.GeocodeResult;
import com.dedicatedcode.reitti.service.geocoding.GeocodeService;
import com.dedicatedcode.reitti.service.geocoding.GeocodeServiceManager;
import com.dedicatedcode.reitti.service.queue.RedisQueueService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static com.dedicatedcode.reitti.model.geocoding.GeocoderType.GEO_APIFY;
import static com.dedicatedcode.reitti.service.MessageDispatcherService.PLACE_CREATED_QUEUE;

@Controller
@RequestMapping("/settings/geocode-services")
public class GeoCodingSettingsController {

    private final GeocodeServiceJdbcService geocodeServiceJdbcService;
    private final GeocodeServiceManager geocodeServiceManager;
    private final SignificantPlaceJdbcService placeJdbcService;
    private final SignificantPlaceOverrideJdbcService significantPlaceOverrideJdbcService;
    private final UserJdbcService userJdbcService;
    private final RedisQueueService messageEnqueuer;
    private final I18nService i18n;
    private final boolean dataManagementEnabled;
    private final int maxErrors;
    private final boolean photonConfigured;
    private final String photonBaseUrl;

    public GeoCodingSettingsController(GeocodeServiceJdbcService geocodeServiceJdbcService,
                                       GeocodeServiceManager geocodeServiceManager,
                                       SignificantPlaceJdbcService placeJdbcService,
                                       SignificantPlaceOverrideJdbcService significantPlaceOverrideJdbcService,
                                       UserJdbcService userJdbcService,
                                       RedisQueueService messageEnqueuer,
                                       I18nService i18n,
                                       @Value("${reitti.geocoding.photon.base-url:}") String photonBaseUrl,
                                       @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled,
                                       @Value("${reitti.geocoding.max-errors}") int maxErrors) {
        this.geocodeServiceJdbcService = geocodeServiceJdbcService;
        this.geocodeServiceManager = geocodeServiceManager;
        this.placeJdbcService = placeJdbcService;
        this.significantPlaceOverrideJdbcService = significantPlaceOverrideJdbcService;
        this.userJdbcService = userJdbcService;
        this.messageEnqueuer = messageEnqueuer;
        this.i18n = i18n;
        this.dataManagementEnabled = dataManagementEnabled;
        this.maxErrors = maxErrors;
        this.photonConfigured = StringUtils.hasText(photonBaseUrl);
        this.photonBaseUrl = photonBaseUrl;
    }

    @GetMapping
    public String getPage(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("activeSection", "geocode-services");
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        model.addAttribute("photonConfigured", photonConfigured);
        model.addAttribute("photonBaseUrl", photonBaseUrl);
        model.addAttribute("geocodeServices", geocodeServiceJdbcService.findAllByOrderByPriorityAndNameAsc());
        model.addAttribute("geocodeServiceTypes", Arrays.stream(GeocoderType.values()).sorted(Comparator.comparing(Enum::name)));
        model.addAttribute("maxErrors", maxErrors);
        return "settings/geocode-services";
    }

    @GetMapping("/geocode-services-content")
    public String getGeocodeServicesContent(Model model) {
        model.addAttribute("geocodeServices", geocodeServiceJdbcService.findAllByOrderByPriorityAndNameAsc());
        model.addAttribute("photonConfigured", photonConfigured);
        model.addAttribute("photonBaseUrl", photonBaseUrl);
        model.addAttribute("geocodeServiceTypes", Arrays.stream(GeocoderType.values()).sorted(Comparator.comparing(Enum::name)));
        model.addAttribute("maxErrors", maxErrors);
        return "settings/geocode-services :: geocode-services-content";
    }

    @GetMapping("/type-fields")
    public String getTypeFields(@RequestParam GeocoderType type, Model model) {
        model.addAttribute("type", type);
        return "settings/fragments/geocoding :: type-fields";
    }

    @GetMapping("/edit/{id}")
    public String editService(@PathVariable Long id, Model model) {
        GeocodeService service = geocodeServiceJdbcService.findById(id).orElseThrow();
        model.addAttribute("service", service);
        model.addAttribute("type", service.getType());
        model.addAttribute("geocodeServiceTypes", Arrays.stream(GeocoderType.values()).sorted(Comparator.comparing(Enum::name)));
        return "settings/fragments/geocoding :: edit-form";
    }

    @PostMapping("/test-config")
    public String testConfiguration(@RequestParam GeocoderType type,
                                    @RequestParam(required = false) String url,
                                    @RequestParam(required = false) String apiKey,
                                    @RequestParam(required = false) String lang,
                                    @RequestParam(required = false) Integer limit,
                                    @RequestParam(required = false) Double radius,
                                    Model model) {
        try {
            double testLat = 48.8584;
            double testLng = 2.2945;
            GeocodeService tmpService = verifySelection(type, url, apiKey, lang, limit, radius);

            GeocodeResult result = geocodeServiceManager.test(tmpService, testLat, testLng);
            model.addAttribute("testResult", result);
        } catch (Exception e) {
            model.addAttribute("testError", e.getMessage());
        }
        return "settings/fragments/geocoding :: test-result-display";
    }

    private GeocodeService verifySelection(GeocoderType type, String url, String apiKey, String lang, Integer limit, Double radius) {
        if (type != GEO_APIFY && (url == null || url.isEmpty())) {
            throw new IllegalArgumentException("Url must not be empty");
        }
        if (Objects.requireNonNull(type) == GEO_APIFY) {
            if (apiKey == null || apiKey.isEmpty()) {
                throw new IllegalArgumentException("Api key must not be empty");
            }
        }
        url = type == GEO_APIFY ? "https://api.geoapify.com/" : url;
        Map<String, String> params = new HashMap<>();
        if (lang != null && !lang.isEmpty()) {
            params.put("language", lang);
        }
        if (limit != null) {
            params.put("limit", limit.toString());
        }
        if (radius != null) {
            params.put("radius", radius.toString());
        }
        if (apiKey != null && !apiKey.isEmpty()) {
            params.put("apiKey", apiKey);
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return new GeocodeService("TMP", url, false, 0, null, null, type, 0, params);
    }

    @PostMapping
    public String saveGeocodeService(@RequestParam(required = false) Long id,
                                       @RequestParam String name,
                                       @RequestParam(required = false) String url,
                                       @RequestParam GeocoderType type,
                                       @RequestParam(required = false) String apiKey,
                                       @RequestParam(required = false) String language,
                                       @RequestParam(required = false) Integer limit,
                                       @RequestParam(required = false) Double radius,
                                       @RequestParam int priority,
                                       Model model) {
        try {
            Map<String, String> params = new HashMap<>();
            if (language != null && !language.isEmpty()) {
                params.put("language", language);
            }
            if (limit != null) {
                params.put("limit", limit.toString());
            }
            if (radius != null) {
                params.put("radius", radius.toString());
            }
            if (apiKey != null && !apiKey.isEmpty()) {
                params.put("apiKey", apiKey);
            }
            if (type == GEO_APIFY) {
                url = "https://api.geoapify.com";
            }
            if (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }

            GeocodeService service;
            if (id != null) {
                GeocodeService existing = geocodeServiceJdbcService.findById(id).orElseThrow();
                service = new GeocodeService(id, name, url, existing.isEnabled(), existing.getErrorCount(), existing.getLastUsed(), existing.getLastError(), type, params, priority, existing.getVersion());
                model.addAttribute("successMessage", i18n.translate("message.success.geocode.updated"));
            } else {
                service = new GeocodeService(name, url, true, 0, null, null, type, priority, params);
                model.addAttribute("successMessage", i18n.translate("message.success.geocode.created"));
            }

            geocodeServiceJdbcService.save(service);
        } catch (Exception e) {
            model.addAttribute("errorMessage", i18n.translate("message.error.geocode.creation", e.getMessage()));
        }

        addDefaultModeAttributes(model);
        return "settings/geocode-services :: geocode-services-content";
    }

    @PostMapping("/{id}/toggle")
    public String toggleGeocodeService(@PathVariable Long id, Model model) {
        GeocodeService service = geocodeServiceJdbcService.findById(id).orElseThrow();
        service = service.withEnabled(!service.isEnabled());
        if (service.isEnabled()) {
            service = service.resetErrorCount();
        }
        geocodeServiceJdbcService.save(service);
        addDefaultModeAttributes(model);

        return "settings/geocode-services :: geocode-services-content";
    }

    @PostMapping("/{id}/delete")
    public String deleteGeocodeService(@PathVariable Long id, Model model) {
        GeocodeService service = geocodeServiceJdbcService.findById(id).orElseThrow();
        geocodeServiceJdbcService.delete(service);
        addDefaultModeAttributes(model);

        return "settings/geocode-services :: geocode-services-content";
    }

    @PostMapping("/{id}/reset-errors")
    public String resetGeocodeServiceErrors(@PathVariable Long id, Model model) {
        GeocodeService service = geocodeServiceJdbcService.findById(id).orElseThrow();
        geocodeServiceJdbcService.save(service.resetErrorCount().withEnabled(true));
        addDefaultModeAttributes(model);

        return "settings/geocode-services :: geocode-services-content";
    }

    @PostMapping("/run-geocoding")
    public String runGeocoding(Authentication authentication, Model model) {
        try {
            String username = authentication.getName();
            User currentUser = userJdbcService.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

            List<SignificantPlace> nonGeocodedPlaces = placeJdbcService.findNonGeocodedByUser(currentUser);

            if (nonGeocodedPlaces.isEmpty()) {
                model.addAttribute("successMessage", i18n.translate("geocoding.no.places"));
            } else {
                for (SignificantPlace place : nonGeocodedPlaces) {
                    SignificantPlaceCreatedEvent event = new SignificantPlaceCreatedEvent(
                            username,
                            null,
                            place.getId(),
                            place.getLatitudeCentroid(),
                            place.getLongitudeCentroid(),
                            UUID.randomUUID().toString()
                    );
                    messageEnqueuer.enqueue(PLACE_CREATED_QUEUE, event);
                }

                model.addAttribute("successMessage", i18n.translate("geocoding.run.success", nonGeocodedPlaces.size()));
            }
        } catch (Exception e) {
            model.addAttribute("errorMessage", i18n.translate("geocoding.run.error", e.getMessage()));
        }

        addDefaultModeAttributes(model);

        return "settings/geocode-services :: geocode-services-content";
    }

    @PostMapping("/clear-and-rerun")
    public String clearAndRerunGeocoding(Authentication authentication, Model model) {
        try {
            String username = authentication.getName();
            User currentUser = userJdbcService.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

            List<SignificantPlace> allPlaces = placeJdbcService.findAllByUser(currentUser);

            if (allPlaces.isEmpty()) {
                model.addAttribute("successMessage", i18n.translate("geocoding.no.places"));
            } else {
                for (SignificantPlace place : allPlaces) {
                    SignificantPlace clearedPlace = place.withGeocoded(false).withAddress(null);
                    this.significantPlaceOverrideJdbcService.clear(currentUser, clearedPlace);
                    placeJdbcService.update(clearedPlace);
                }

                for (SignificantPlace place : allPlaces) {
                    SignificantPlaceCreatedEvent event = new SignificantPlaceCreatedEvent(
                            username,
                            null,
                            place.getId(),
                            place.getLatitudeCentroid(),
                            place.getLongitudeCentroid(),
                            UUID.randomUUID().toString()
                    );
                    messageEnqueuer.enqueue(PLACE_CREATED_QUEUE, event);
                }

                model.addAttribute("successMessage", i18n.translate("geocoding.clear.success", allPlaces.size()));
            }
        } catch (Exception e) {
            model.addAttribute("errorMessage", i18n.translate("geocoding.clear.error", e.getMessage()));
        }

        addDefaultModeAttributes(model);

        return "settings/geocode-services :: geocode-services-content";
    }

    private void addDefaultModeAttributes(Model model) {
        model.addAttribute("geocodeServices", geocodeServiceJdbcService.findAllByOrderByPriorityAndNameAsc());
        model.addAttribute("photonConfigured", photonConfigured);
        model.addAttribute("photonBaseUrl", photonBaseUrl);
        model.addAttribute("maxErrors", maxErrors);
        model.addAttribute("geocodeServiceTypes", Arrays.stream(GeocoderType.values()).sorted(Comparator.comparing(Enum::name)));
    }
}
