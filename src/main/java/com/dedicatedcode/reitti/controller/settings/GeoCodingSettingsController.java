package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.SignificantPlaceCreatedEvent;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geocoding.RemoteGeocodeService;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.GeocodeServiceJdbcService;
import com.dedicatedcode.reitti.repository.SignificantPlaceJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/settings/geocode-services")
public class GeoCodingSettingsController {

    private final GeocodeServiceJdbcService geocodeServiceJdbcService;
    private final SignificantPlaceJdbcService placeJdbcService;
    private final UserJdbcService userJdbcService;
    private final RabbitTemplate rabbitTemplate;
    private final MessageSource messageSource;

    private final boolean dataManagementEnabled;
    private final int maxErrors;

    public GeoCodingSettingsController(GeocodeServiceJdbcService geocodeServiceJdbcService,
                                       SignificantPlaceJdbcService placeJdbcService,
                                       UserJdbcService userJdbcService,
                                       RabbitTemplate rabbitTemplate,
                                       MessageSource messageSource,
                                       @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled,
                                       @Value("${reitti.geocoding.max-errors}") int maxErrors) {
        this.geocodeServiceJdbcService = geocodeServiceJdbcService;
        this.placeJdbcService = placeJdbcService;
        this.userJdbcService = userJdbcService;
        this.rabbitTemplate = rabbitTemplate;
        this.messageSource = messageSource;
        this.dataManagementEnabled = dataManagementEnabled;
        this.maxErrors = maxErrors;
    }

    @GetMapping
    public String getPage(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("activeSection", "geocode-services");
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        model.addAttribute("geocodeServices", geocodeServiceJdbcService.findAllByOrderByNameAsc());
        model.addAttribute("maxErrors", maxErrors);
        return "settings/geocode-services";
    }

    @GetMapping("/geocode-services-content")
    public String getGeocodeServicesContent(Model model) {
        model.addAttribute("geocodeServices", geocodeServiceJdbcService.findAllByOrderByNameAsc());
        model.addAttribute("maxErrors", maxErrors);
        return "settings/geocode-services :: geocode-services-content";
    }

    @PostMapping
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
        return "settings/geocode-services :: geocode-services-content";
    }

    @PostMapping("/{id}/toggle")
    public String toggleGeocodeService(@PathVariable Long id, Model model) {
        RemoteGeocodeService service = geocodeServiceJdbcService.findById(id).orElseThrow();
        service = service.withEnabled(!service.isEnabled());
        if (service.isEnabled()) {
            service = service.resetErrorCount();
        }
        geocodeServiceJdbcService.save(service);
        model.addAttribute("geocodeServices", geocodeServiceJdbcService.findAllByOrderByNameAsc());
        model.addAttribute("maxErrors", maxErrors);
        return "settings/geocode-services :: geocode-services-content";
    }

    @PostMapping("/{id}/delete")
    public String deleteGeocodeService(@PathVariable Long id, Model model) {
        RemoteGeocodeService service = geocodeServiceJdbcService.findById(id).orElseThrow();
        geocodeServiceJdbcService.delete(service);
        model.addAttribute("geocodeServices", geocodeServiceJdbcService.findAllByOrderByNameAsc());
        model.addAttribute("maxErrors", maxErrors);
        return "settings/geocode-services :: geocode-services-content";
    }

    @PostMapping("/{id}/reset-errors")
    public String resetGeocodeServiceErrors(@PathVariable Long id, Model model) {
        RemoteGeocodeService service = geocodeServiceJdbcService.findById(id).orElseThrow();
        geocodeServiceJdbcService.save(service.resetErrorCount().withEnabled(true));
        model.addAttribute("geocodeServices", geocodeServiceJdbcService.findAllByOrderByNameAsc());
        model.addAttribute("maxErrors", maxErrors);
        return "settings/geocode-services :: geocode-services-content";
    }

    @PostMapping("/run-geocoding")
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
        return "settings/geocode-services :: geocode-services-content";
    }

    @PostMapping("/clear-and-rerun")
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
        return "settings/geocode-services :: geocode-services-content";
    }


    private String getMessage(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

}
