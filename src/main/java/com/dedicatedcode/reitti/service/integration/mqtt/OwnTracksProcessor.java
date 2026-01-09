package com.dedicatedcode.reitti.service.integration.mqtt;

import com.dedicatedcode.reitti.model.security.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class OwnTracksProcessor implements MqttPayloadProcessor {
    private static final Logger log = LoggerFactory.getLogger(OwnTracksProcessor.class);
    @Override
    public PayloadType getSupportedType() {
        return PayloadType.OWNTRACKS;
    }

    @Override
    public void process(User user, byte[] payload) {
        String json = new String(payload, StandardCharsets.UTF_8);
        log.info("Processing OwnTracks data for user {}: {}", user, json);
        // Implementation: Parse JSON, check if _type == "location", save to DB
    }
}