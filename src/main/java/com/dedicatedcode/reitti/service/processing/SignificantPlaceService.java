package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.SignificantPlaceCreatedEvent;
import com.dedicatedcode.reitti.model.SignificantPlace;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.model.Visit;
import com.dedicatedcode.reitti.repository.SignificantPlaceRepository;
import com.dedicatedcode.reitti.repository.VisitRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class SignificantPlaceService {
    private static final Logger logger = LoggerFactory.getLogger(SignificantPlaceService.class);

    // Parameters for significant place detection
    private static final double PLACE_MERGE_DISTANCE = 0.001; // degrees
    private static final int SRID = 4326;

    private final SignificantPlaceRepository significantPlaceRepository;
    private final VisitRepository visitRepository;
    private final GeometryFactory geometryFactory;
    private final RabbitTemplate rabbitTemplate;

    @Autowired
    public SignificantPlaceService(
            SignificantPlaceRepository significantPlaceRepository,
            VisitRepository visitRepository,
            RabbitTemplate rabbitTemplate) {
        this.significantPlaceRepository = significantPlaceRepository;
        this.visitRepository = visitRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.geometryFactory = new GeometryFactory(new PrecisionModel(), SRID);
    }

    public List<SignificantPlace> processStayPoints(User user, List<StayPoint> stayPoints) {
        logger.info("Processing {} stay points for user {}", stayPoints.size(), user.getUsername());

        List<SignificantPlace> updatedPlaces = new ArrayList<>();

        for (StayPoint stayPoint : stayPoints) {
            // Find existing places near this stay point
            List<SignificantPlace> nearbyPlaces = findNearbyPlaces(user, stayPoint.getLatitude(), stayPoint.getLongitude());

            if (nearbyPlaces.isEmpty()) {
                // Create a new significant place
                SignificantPlace newPlace = createSignificantPlace(user, stayPoint);
                significantPlaceRepository.save(newPlace);

                // Create a visit for this place
                Visit visit = createVisit(user, newPlace, stayPoint);
                visitRepository.save(visit);

                updatedPlaces.add(newPlace);
                logger.info("Created new significant place at ({}, {})", stayPoint.getLatitude(), stayPoint.getLongitude());

                // Publish event for the new place
                publishSignificantPlaceCreatedEvent(newPlace);
            } else {
                // Update the first existing place
                Optional<SignificantPlace> existingPlace = this.significantPlaceRepository.findById(nearbyPlaces.get(0).getId());
                existingPlace.ifPresent(place -> {
                    // Create a visit for this place
                    Visit visit = createVisit(user, place, stayPoint);
                    visitRepository.save(visit);
                    updatedPlaces.add(place);
                });
            }
        }

        return updatedPlaces;
    }

    private List<SignificantPlace> findNearbyPlaces(User user, double latitude, double longitude) {
        // Create a point geometry
        Point point = geometryFactory.createPoint(new Coordinate(longitude, latitude));

        // Find places within the merge distance
        return significantPlaceRepository.findNearbyPlaces(user.getId(), point, PLACE_MERGE_DISTANCE);
    }

    private SignificantPlace createSignificantPlace(User user, StayPoint stayPoint) {
        // Create a point geometry
        Point point = geometryFactory.createPoint(new Coordinate(stayPoint.getLongitude(), stayPoint.getLatitude()));

        return new SignificantPlace(
                user,
                null, // name will be set later through reverse geocoding or user input
                null, // address will be set later through reverse geocoding
                stayPoint.getLatitude(),
                stayPoint.getLongitude(),
                point,
                null
        );
    }

    private Visit createVisit(User user, SignificantPlace place, StayPoint stayPoint) {
        Visit visit = new Visit();
        visit.setUser(user);
        visit.setPlace(place);
        visit.setStartTime(stayPoint.getArrivalTime());
        visit.setEndTime(stayPoint.getDepartureTime());
        visit.setDurationSeconds(stayPoint.getDurationSeconds());
        return visit;
    }

    private void publishSignificantPlaceCreatedEvent(SignificantPlace place) {
        SignificantPlaceCreatedEvent event = new SignificantPlaceCreatedEvent(
                place.getId(),
                place.getLatitudeCentroid(),
                place.getLongitudeCentroid()
        );
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.SIGNIFICANT_PLACE_ROUTING_KEY, event);
        logger.info("Published SignificantPlaceCreatedEvent for place ID: {}", place.getId());
    }
}
