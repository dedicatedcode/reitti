package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
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
import com.dedicatedcode.reitti.service.geocoding.GeocodeService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/settings/geocode-services")
public class GeoCodingSettingsController {

    private final GeocodeServiceJdbcService geocodeServiceJdbcService;
    private final SignificantPlaceJdbcService placeJdbcService;
    private final SignificantPlaceOverrideJdbcService significantPlaceOverrideJdbcService;
    private final UserJdbcService userJdbcService;
    private final RabbitTemplate rabbitTemplate;
    private final I18nService i18n;
    private final boolean dataManagementEnabled;
    private final int maxErrors;
    private final boolean photonConfigured;
    private final String photonBaseUrl;

    public GeoCodingSettingsController(GeocodeServiceJdbcService geocodeServiceJdbcService,
                                       SignificantPlaceJdbcService placeJdbcService,
                                       SignificantPlaceOverrideJdbcService significantPlaceOverrideJdbcService,
                                       UserJdbcService userJdbcService,
                                       RabbitTemplate rabbitTemplate,
                                       I18nService i18n,
                                       @Value("${reitti.geocoding.photon.base-url:}") String photonBaseUrl,
                                       @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled,
                                       @Value("${reitti.geocoding.max-errors}") int maxErrors) {
        this.geocodeServiceJdbcService = geocodeServiceJdbcService;
        this.placeJdbcService = placeJdbcService;
        this.significantPlaceOverrideJdbcService = significantPlaceOverrideJdbcService;
        this.userJdbcService = userJdbcService;
        this.rabbitTemplate = rabbitTemplate;
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
        model.addAttribute("geocodeServices", geocodeServiceJdbcService.findAllByOrderByNameAsc());
        model.addAttribute("geocodeServiceTypes", Arrays.stream(GeocoderType.values()).sorted(Comparator.comparing(Enum::name)));
        model.addAttribute("maxErrors", maxErrors);
        return "settings/geocode-services";
    }

    @GetMapping("/geocode-services-content")
    public String getGeocodeServicesContent(Model model) {
        model.addAttribute("geocodeServices", geocodeServiceJdbcService.findAllByOrderByNameAsc());
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
    //Todo: make this generic
    @PostMapping("/test-photon")
    public String testPhotonConnection(Model model) {
        try {
            if (!photonConfigured || !StringUtils.hasText(photonBaseUrl)) {
                model.addAttribute("errorMessage", i18n.translate("geocoding.photon.not.configured"));
            } else {
                // Use RestTemplate to test the connection
                RestTemplate restTemplate = new RestTemplate();
                String response = restTemplate.getForObject(null, String.class);

                if (response != null && response.contains("\"type\":\"FeatureCollection\"")) {
                    model.addAttribute("successMessage", i18n.translate("geocoding.photon.test.success"));
                } else {
                    model.addAttribute("errorMessage", i18n.translate("geocoding.photon.test.invalid.response"));
                }
            }
        } catch (Exception e) {
            model.addAttribute("errorMessage", i18n.translate("geocoding.photon.test.error", e.getMessage()));
        }

        model.addAttribute("geocodeServices", geocodeServiceJdbcService.findAllByOrderByNameAsc());
        model.addAttribute("photonConfigured", photonConfigured);
        model.addAttribute("photonBaseUrl", photonBaseUrl);
        model.addAttribute("maxErrors", maxErrors);
        return "settings/geocode-services :: geocode-services-content";
    }

    @PostMapping
    public String createGeocodeService(@RequestParam String name,
                                       @RequestParam String urlTemplate,
                                       @RequestParam GeocoderType type,
                                       @RequestParam int priority,
                                       Model model) {
        try {
            GeocodeService service = new GeocodeService(name, urlTemplate, true, 0, null, null, type, priority);
            geocodeServiceJdbcService.save(service);
            model.addAttribute("successMessage", i18n.translate("message.success.geocode.created"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", i18n.translate("message.error.geocode.creation", e.getMessage()));
        }

        model.addAttribute("geocodeServices", geocodeServiceJdbcService.findAllByOrderByNameAsc());
        model.addAttribute("photonConfigured", photonConfigured);
        model.addAttribute("photonBaseUrl", photonBaseUrl);
        model.addAttribute("maxErrors", maxErrors);
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
        model.addAttribute("geocodeServices", geocodeServiceJdbcService.findAllByOrderByNameAsc());
        model.addAttribute("photonConfigured", photonConfigured);
        model.addAttribute("photonBaseUrl", photonBaseUrl);
        model.addAttribute("maxErrors", maxErrors);
        return "settings/geocode-services :: geocode-services-content";
    }

    @PostMapping("/{id}/delete")
    public String deleteGeocodeService(@PathVariable Long id, Model model) {
        GeocodeService service = geocodeServiceJdbcService.findById(id).orElseThrow();
        geocodeServiceJdbcService.delete(service);
        model.addAttribute("geocodeServices", geocodeServiceJdbcService.findAllByOrderByNameAsc());
        model.addAttribute("photonConfigured", photonConfigured);
        model.addAttribute("photonBaseUrl", photonBaseUrl);
        model.addAttribute("maxErrors", maxErrors);
        return "settings/geocode-services :: geocode-services-content";
    }

    @PostMapping("/{id}/reset-errors")
    public String resetGeocodeServiceErrors(@PathVariable Long id, Model model) {
        GeocodeService service = geocodeServiceJdbcService.findById(id).orElseThrow();
        geocodeServiceJdbcService.save(service.resetErrorCount().withEnabled(true));
        model.addAttribute("geocodeServices", geocodeServiceJdbcService.findAllByOrderByNameAsc());
        model.addAttribute("photonConfigured", photonConfigured);
        model.addAttribute("photonBaseUrl", photonBaseUrl);
        model.addAttribute("maxErrors", maxErrors);
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
                    rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.SIGNIFICANT_PLACE_ROUTING_KEY, event);
                }

                model.addAttribute("successMessage", i18n.translate("geocoding.run.success", nonGeocodedPlaces.size()));
            }
        } catch (Exception e) {
            model.addAttribute("errorMessage", i18n.translate("geocoding.run.error", e.getMessage()));
        }

        model.addAttribute("geocodeServices", geocodeServiceJdbcService.findAllByOrderByNameAsc());
        model.addAttribute("photonConfigured", photonConfigured);
        model.addAttribute("photonBaseUrl", photonBaseUrl);
        model.addAttribute("maxErrors", maxErrors);
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
                    rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.SIGNIFICANT_PLACE_ROUTING_KEY, event);
                }

                model.addAttribute("successMessage", i18n.translate("geocoding.clear.success", allPlaces.size()));
            }
        } catch (Exception e) {
            model.addAttribute("errorMessage", i18n.translate("geocoding.clear.error", e.getMessage()));
        }

        model.addAttribute("geocodeServices", geocodeServiceJdbcService.findAllByOrderByNameAsc());
        model.addAttribute("photonConfigured", photonConfigured);
        model.addAttribute("photonBaseUrl", photonBaseUrl);
        model.addAttribute("maxErrors", maxErrors);
        return "settings/geocode-services :: geocode-services-content";
    }
}
