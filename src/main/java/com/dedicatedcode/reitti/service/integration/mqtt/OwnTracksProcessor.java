package com.dedicatedcode.reitti.service.integration.mqtt;

import com.dedicatedcode.reitti.controller.api.ingestion.owntracks.OwntracksFriendResponse;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.dto.OwntracksLocationRequest;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.LocationBatchingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class OwnTracksProcessor implements MqttPayloadProcessor {
    private static final Logger logger = LoggerFactory.getLogger(OwnTracksProcessor.class);
    private final LocationBatchingService locationBatchingService;
    private final ObjectMapper objectMapper;

    public OwnTracksProcessor(LocationBatchingService locationBatchingService, ObjectMapper objectMapper) {
        this.locationBatchingService = locationBatchingService;
        this.objectMapper = objectMapper;
    }

    @Override
    public PayloadType getSupportedType() {
        return PayloadType.OWNTRACKS;
    }

    @Override
    public void process(User user, byte[] payload) {
        String json = new String(payload, StandardCharsets.UTF_8);
        logger.info("Processing OwnTracks data for user {}: {}", user, json);

        try {
            OwntracksLocationRequest request = this.objectMapper.readValue(json, OwntracksLocationRequest.class);
            if (!request.isLocationUpdate()) {
                logger.debug("Ignoring non-location Owntracks message of type: {}", request.getType());
                return;
            }

            LocationPoint locationPoint = request.toLocationPoint();

            if (locationPoint.getTimestamp() == null) {
                logger.warn("Ignoring location point [{}] because timestamp is null", locationPoint);
                return;
            }

            this.locationBatchingService.addLocationPoint(user, locationPoint);
            logger.debug("Successfully received and queued Owntracks location point for user {}",
                         user.getUsername());

        } catch (Exception e) {
            logger.error("Error processing Owntracks data", e);
        }
    }
}