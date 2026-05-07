package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeoJsonExportService {

    private final RawLocationPointRepository rawLocationPointRepository;
    private final ObjectMapper objectMapper;

    public GeoJsonExportService(RawLocationPointRepository rawLocationPointRepository,
                                ObjectMapper objectMapper) {
        this.rawLocationPointRepository = rawLocationPointRepository;
        this.objectMapper = objectMapper;
    }

    public void generateGeoJsonContentStreaming(User user,
                                                Instant start,
                                                Instant end,
                                                Long deviceId,
                                                Writer writer) throws IOException {
        List<RawLocationPoint> points = fetchPoints(user.getId(), start, end, deviceId);

        FeatureCollection collection = new FeatureCollection();
        for (RawLocationPoint point : points) {
            Feature feature = new Feature();
            feature.setGeometry(createPointGeometry(point));
            feature.setProperties(createProperties(point));
            collection.getFeatures().add(feature);
        }

        objectMapper.writeValue(writer, collection);
    }

    private List<RawLocationPoint> fetchPoints(Long userId,
                                               Instant start,
                                               Instant end,
                                               Long deviceId) {
        // Use UTC for conversion because raw timestamps are stored in UTC
        ZonedDateTime startDateTime = start.atZone(ZoneId.of("UTC"));
        ZonedDateTime endDateTime = end.atZone(ZoneId.of("UTC"));

        if (deviceId != null) {
            return rawLocationPointRepository.findByUserIdAndTimestampBetweenAndDevice(
                    userId,
                    startDateTime.toLocalDateTime(),
                    endDateTime.toLocalDateTime(),
                    deviceId);
        } else {
            return rawLocationPointRepository.findByUserIdAndTimestampBetween(
                    userId,
                    startDateTime.toLocalDateTime(),
                    endDateTime.toLocalDateTime());
        }
    }

    private Geometry createPointGeometry(RawLocationPoint point) {
        List<Double> coordinates = new ArrayList<>();
        // GeoJSON expects [longitude, latitude]
        coordinates.add(point.getGeom().getLongitude());
        coordinates.add(point.getGeom().getLatitude());
        if (point.getGeom().getAltitude() != null) {
            coordinates.add(point.getGeom().getAltitude());
        }
        Geometry geometry = new Geometry();
        geometry.setType("Point");
        geometry.setCoordinates(coordinates);
        return geometry;
    }

    private Map<String, Object> createProperties(RawLocationPoint point) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", point.getId());
        props.put("timestamp", point.getTimestamp().toString());
        props.put("accuracy", point.getAccuracyMeters());
        props.put("elevation", point.getElevationMeters());
        return props;
    }

    // ---- simple DTOs for serialisation ----

    static class FeatureCollection {
        private String type = "FeatureCollection";
        private List<Feature> features = new ArrayList<>();

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public List<Feature> getFeatures() { return features; }
        public void setFeatures(List<Feature> features) { this.features = features; }
    }

    static class Feature {
        private String type = "Feature";
        private Geometry geometry;
        private Map<String, Object> properties;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Geometry getGeometry() { return geometry; }
        public void setGeometry(Geometry geometry) { this.geometry = geometry; }
        public Map<String, Object> getProperties() { return properties; }
        public void setProperties(Map<String, Object> properties) { this.properties = properties; }
    }

    static class Geometry {
        private String type;
        private List<Double> coordinates;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public List<Double> getCoordinates() { return coordinates; }
        public void setCoordinates(List<Double> coordinates) { this.coordinates = coordinates; }
    }
}
