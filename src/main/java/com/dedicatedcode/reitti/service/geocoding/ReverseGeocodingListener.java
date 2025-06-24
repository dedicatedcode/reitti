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
            Optional<GeocodeResult> resultOpt = this.geocodeServiceManager.reverseGeocode(place.getLatitudeCentroid(), place.getLongitudeCentroid());

            if (resultOpt.isPresent()) {
                GeocodeResult result = resultOpt.get();
                String label = result.label();
                String street = result.street();
                String city = result.city();
                String district = result.district();

                // Set the name to the street or district if available
                if (!street.isEmpty()) {
                    place = place.withName(street);
                } else if (!district.isEmpty()) {
                    place = place.withName(district);
                } else if (!city.isEmpty()) {
                    place = place.withName(city);
                }

                significantPlaceJdbcService.update(place.withAddress(label).withGeocoded(true));
                logger.info("Updated place ID: {} with geocoding data: {}", place.getId(), label);
            } else {
                logger.warn("No geocoding results found for place ID: {}", place.getId());
            }
        } catch (Exception e) {
            logger.error("Error during reverse geocoding for place ID: {}", place.getId(), e);
        }
    }
}
