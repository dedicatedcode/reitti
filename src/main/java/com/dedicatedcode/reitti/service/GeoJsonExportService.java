package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.geo.SourceLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.util.*;

@Service
public class GeoJsonExportService {

    private final SourceLocationPointJdbcService rawLocationPointRepository;
    private final DeviceJdbcService deviceJdbcService;
    private final ObjectMapper objectMapper;

    public GeoJsonExportService(SourceLocationPointJdbcService rawLocationPointRepository,
                                DeviceJdbcService deviceJdbcService,
                                ObjectMapper objectMapper) {
        this.rawLocationPointRepository = rawLocationPointRepository;
        this.deviceJdbcService = deviceJdbcService;
        this.objectMapper = objectMapper;
    }

    public void generateGeoJsonContentStreaming(User user,
                                                Instant start,
                                                Instant end,
                                                Long deviceId,
                                                Writer writer) throws IOException {
        List<SourceLocationPoint> points = fetchPoints(user, start, end, deviceId);

        FeatureCollection collection = new FeatureCollection();
        for (SourceLocationPoint point : points) {
            Feature feature = new Feature();
            feature.setGeometry(createPointGeometry(point));
            feature.setProperties(createProperties(point));
            collection.getFeatures().add(feature);
        }

        objectMapper.writeValue(writer, collection);
    }

    private List<SourceLocationPoint> fetchPoints(User user,
                                               Instant start,
                                               Instant end,
                                               Long deviceId) {
        Optional<Device> device = this.deviceJdbcService.find(user, deviceId);
            return rawLocationPointRepository.findByUserAndTimestampBetweenOrderByTimestampAsc(
                    user,
                    device.orElse(null),
                    start,
                    end,
                    true, true);
    }

    private Geometry createPointGeometry(SourceLocationPoint point) {
        List<Double> coordinates = new ArrayList<>();
        // GeoJSON expects [longitude, latitude]
        coordinates.add(point.getGeom().longitude());
        coordinates.add(point.getGeom().latitude());
        if (point.getElevationMeters() != null) {
            coordinates.add(point.getElevationMeters());
        }
        Geometry geometry = new Geometry();
        geometry.setType("Point");
        geometry.setCoordinates(coordinates);
        return geometry;
    }

    private Map<String, Object> createProperties(SourceLocationPoint point) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", point.getId());
        props.put("timestamp", point.getTimestamp().toString());
        props.put("accuracy", point.getAccuracyMeters());
        props.put("elevation", point.getElevationMeters());
        return props;
    }

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
