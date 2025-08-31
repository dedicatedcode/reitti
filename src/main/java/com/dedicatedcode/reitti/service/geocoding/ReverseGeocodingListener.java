package com.dedicatedcode.reitti.service.geocoding;

import com.dedicatedcode.reitti.event.SignificantPlaceCreatedEvent;
import com.dedicatedcode.reitti.model.SignificantPlace;
import com.dedicatedcode.reitti.repository.SignificantPlaceJdbcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ReverseGeocodingListener {
    private static final Logger logger = LoggerFactory.getLogger(ReverseGeocodingListener.class);

    private final SignificantPlaceJdbcService significantPlaceJdbcService;
    private final GeocodeServiceManager geocodeServiceManager;

    @Autowired
    public ReverseGeocodingListener(SignificantPlaceJdbcService significantPlaceJdbcService,
                                    GeocodeServiceManager geocodeServiceManager) {
        this.significantPlaceJdbcService = significantPlaceJdbcService;
        this.geocodeServiceManager = geocodeServiceManager;
    }

    public void handleSignificantPlaceCreated(SignificantPlaceCreatedEvent event) {
        logger.info("Received SignificantPlaceCreatedEvent for place ID: {}", event.getPlaceId());

        Optional<SignificantPlace> placeOptional = significantPlaceJdbcService.findById(event.getPlaceId());
        if (placeOptional.isEmpty()) {
            logger.error("Could not find SignificantPlace with ID: {}", event.getPlaceId());
            return;
        }

        SignificantPlace place = placeOptional.get();

        try {
            Optional<GeocodeResult> resultOpt = this.geocodeServiceManager.reverseGeocode(place);

            if (resultOpt.isPresent()) {
                GeocodeResult result = resultOpt.get();
                String label = result.label();
                String street = result.street();
                String houseNumber = result.houseNumber();
                String postcode = result.postcode();
                String city = result.city();
                SignificantPlace.PlaceType placeType = result.placeType();
                String countryCode = result.countryCode();

                if (!label.isEmpty()) {
                    place = place.withName(label)
                            .withAddress(String.format("%s %s, %s %s", street, houseNumber, postcode, city));
                } else {
                    place = place.withName(street)
                            .withAddress(String.format("%s %s, %s %s", street, houseNumber, postcode, city));
                }
                place = place.withType(placeType).withCountryCode(countryCode);

                significantPlaceJdbcService.update(place.withGeocoded(true));
                logger.info("Updated place ID: {} with geocoding data: {}", place.getId(), label);
            } else {
                logger.warn("No geocoding results found for place ID: {}", place.getId());
            }
        } catch (Exception e) {
            logger.error("Error during reverse geocoding for place ID: {}", place.getId(), e);
        }
    }
}
