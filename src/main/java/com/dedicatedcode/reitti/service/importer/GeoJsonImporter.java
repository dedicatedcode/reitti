package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

@Component
public class GeoJsonImporter {

    private final RawLocationPointRepository rawLocationPointRepository;
    private final ObjectMapper objectMapper;

    public GeoJsonImporter(RawLocationPointRepository rawLocationPointRepository,
                           ObjectMapper objectMapper) {
        this.rawLocationPointRepository = rawLocationPointRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Imports points from a GeoJSON input stream.
     *
     * @param input    the GeoJSON source
     * @param user     the authenticated user
     * @param deviceId optional device ID (nullable)
     * @param fileName original file name for logging
     * @param start    optional lower timestamp bound (nullable)
     * @param end      optional upper timestamp bound (nullable)
     * @return a map containing "success", "pointsImported" and optionally "error"
     */
    public Map<String, Object> importGeoJson(InputStream input,
                                             User user,
                                             Long deviceId,
                                             String fileName,
                                             Instant start,
                                             Instant end) throws IOException {
        Map<String, Object> result = new HashMap<>();
        JsonNode root = objectMapper.readTree(input);

        if (!root.has("type") || !"FeatureCollection".equals(root.get("type").asText())) {
            result.put("success", false);
            result.put("error", "Invalid GeoJSON: root must be a FeatureCollection");
            return result;
        }

        JsonNode features = root.get("features");
        if (features == null || !features.isArray()) {
            result.put("success", false);
            result.put("error", "FeatureCollection must contain a 'features' array");
            return result;
        }

        int imported = 0;
        for (JsonNode featureNode : features) {
            RawLocationPoint point = buildPoint(featureNode, start, end, user.getId(), deviceId);
            if (point != null) {
                rawLocationPointRepository.save(point);
                imported++;
            }
        }

        result.put("success", true);
        result.put("pointsImported", imported);
        return result;
    }

    /**
     * Extracts a {@link RawLocationPoint} from a GeoJSON Feature node.
     * Returns {@code null} if the feature does not represent a valid point,
     * or if its timestamp lies outside the optional time window.
     */
    private RawLocationPoint buildPoint(JsonNode featureNode,
                                        Instant start,
                                        Instant end,
                                        Long userId,
                                        Long deviceId) {
        if (!featureNode.has("geometry")) {
            return null;
        }
        JsonNode geomNode = featureNode.get("geometry");
        if (!"Point".equals(geomNode.get("type").asText())) {
            return null;
        }
        JsonNode coords = geomNode.get("coordinates");
        if (coords == null || !coords.isArray() || coords.size() < 2) {
            return null;
        }

        double lon = coords.get(0).asDouble();
        double lat = coords.get(1).asDouble();
        double alt = 0.0;
        if (coords.size() > 2) {
            alt = coords.get(2).asDouble();
        }

        // Extract timestamp from property "timestamp" (ISO‑8601)
        Instant timestamp = null;
        if (featureNode.has("properties")) {
            JsonNode props = featureNode.get("properties");
            if (props.has("timestamp")) {
                try {
                    timestamp = Instant.parse(props.get("timestamp").asText());
                } catch (DateTimeParseException ignored) {
                    // cannot parse, leave null
                }
            }
        }
        if (timestamp == null) {
            return null; // mandatory
        }

        // optional window check
        if (start != null && timestamp.isBefore(start)) {
            return null;
        }
        if (end != null && timestamp.isAfter(end)) {
            return null;
        }

        Double accuracy = null;
        Double elevation = null;
        if (featureNode.has("properties")) {
            JsonNode props = featureNode.get("properties");
            if (props.has("accuracy")) {
                accuracy = props.get("accuracy").asDouble();
            }
            if (props.has("elevation")) {
                elevation = props.get("elevation").asDouble();
            }
        }

        GeoPoint geoPoint = new GeoPoint(lat, lon, alt);

        // We set processed=false, synthetic=false, invalid=false for incoming points
        return new RawLocationPoint(
                null, // id auto‑generated
                timestamp,
                accuracy,
                elevation,
                geoPoint,
                false, // processed
                false, // synthetic
                false  // invalid
        );
    }
}
