package com.dedicatedcode.reitti.service.geocoding;

import com.dedicatedcode.reitti.event.SignificantPlaceCreatedEvent;
import com.dedicatedcode.reitti.model.PlaceInformationOverride;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.PreviewSignificantPlaceJdbcService;
import com.dedicatedcode.reitti.repository.SignificantPlaceJdbcService;
import com.dedicatedcode.reitti.repository.SignificantPlaceOverrideJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.UserNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ReverseGeocodingListener {
    private static final Logger logger = LoggerFactory.getLogger(ReverseGeocodingListener.class);

    private final SignificantPlaceJdbcService significantPlaceJdbcService;
    private final PreviewSignificantPlaceJdbcService previewSignificantPlaceJdbcService;
    private final GeocodeServiceManager geocodeServiceManager;
    private final SignificantPlaceOverrideJdbcService significantPlaceOverrideJdbcService;
    private final UserNotificationService userNotificationService;
    private final UserJdbcService userJdbcService;

    @Autowired
    public ReverseGeocodingListener(SignificantPlaceJdbcService significantPlaceJdbcService,
                                    PreviewSignificantPlaceJdbcService previewSignificantPlaceJdbcService,
                                    GeocodeServiceManager geocodeServiceManager, SignificantPlaceOverrideJdbcService significantPlaceOverrideJdbcService,
                                    UserNotificationService userNotificationService, UserJdbcService userJdbcService) {
        this.significantPlaceJdbcService = significantPlaceJdbcService;
        this.previewSignificantPlaceJdbcService = previewSignificantPlaceJdbcService;
        this.geocodeServiceManager = geocodeServiceManager;
        this.significantPlaceOverrideJdbcService = significantPlaceOverrideJdbcService;
        this.userNotificationService = userNotificationService;
        this.userJdbcService = userJdbcService;
    }

    public void handleSignificantPlaceCreated(SignificantPlaceCreatedEvent event) {
        logger.info("Received SignificantPlaceCreatedEvent for place ID: {}", event.placeId());

        User user = userJdbcService.findByUsername(event.username()).orElseThrow();
        Optional<SignificantPlace> placeOptional = event.previewId() == null ? significantPlaceJdbcService.findById(event.placeId()) : previewSignificantPlaceJdbcService.findById(event.placeId());
        if (placeOptional.isEmpty()) {
            logger.error("Could not find SignificantPlace with ID: {}", event.placeId());
            return;
        }

        SignificantPlace place = placeOptional.get();

        try {
            Optional<GeocodeResult> resultOpt = this.geocodeServiceManager.reverseGeocode(place, event.previewId() == null);

            if (resultOpt.isPresent()) {
                GeocodeResult result = resultOpt.get();
                String label = result.label();
                String street = result.street();
                String houseNumber = result.houseNumber();
                String postcode = result.postcode();
                String city = result.city();
                SignificantPlace.PlaceType placeType = result.placeType();
                String countryCode = result.countryCode();

                String address = String.format("%s %s, %s %s", street, houseNumber, postcode, city);
                if (!label.isEmpty()) {
                    place = place.withName(label).withAddress(address);
                } else {
                    place = place.withName(street).withAddress(address);
                }
                place = place
                        .withType(placeType)
                        .withCity(city)
                        .withCountryCode(countryCode);

                Optional<PlaceInformationOverride> override = this.significantPlaceOverrideJdbcService.findByUserAndPoint(user, new GeoPoint(place.getLatitudeCentroid(), place.getLongitudeCentroid()));
                if (override.isPresent()) {
                    logger.info("Found override for place ID: {} with name: {}, type: {}, timezone: {}", place.getId(), override.get().name(), override.get().category(), override.get().timezone());
                    place = place.withName(override.get().name()).withType(override.get().category()).withTimezone(override.get().timezone());
                }
                if (event.previewId() == null) {
                    significantPlaceJdbcService.update(place.withGeocoded(true));
                    userNotificationService.placeUpdate(user, place);
                } else {
                    previewSignificantPlaceJdbcService.update(place.withGeocoded(true));
                    userNotificationService.placeUpdate(user, place, event.previewId());

                }
                logger.info("Updated place ID: {} with geocoding data: {}", place.getId(), label);
            } else {
                logger.warn("No geocoding results found for place ID: {}", place.getId());
            }
        } catch (Exception e) {
            logger.error("Error during reverse geocoding for place ID: {}", place.getId(), e);
        }
    }
}
