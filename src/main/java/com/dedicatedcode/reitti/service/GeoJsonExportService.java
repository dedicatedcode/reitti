package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.geo.SourceLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeoJsonExportService {

    private final SourceLocationPointJdbcService sourceLocationPointJdbcService;
    private final RawLocationPointJdbcService rawLocationPointRepository;
    private final DeviceJdbcService deviceJdbcService;
    private final ObjectMapper objectMapper;

    public GeoJsonExportService(SourceLocationPointJdbcService sourceLocationPointJdbcService, RawLocationPointJdbcService rawLocationPointRepository,
                                DeviceJdbcService deviceJdbcService,
                                ObjectMapper objectMapper) {
        this.sourceLocationPointJdbcService = sourceLocationPointJdbcService;
        this.rawLocationPointRepository = rawLocationPointRepository;
        this.deviceJdbcService = deviceJdbcService;
        this.objectMapper = objectMapper;
    }

    public void generateGeoJsonContentStreaming(User user,
                                                Instant start,
                                                Instant end,
                                                Writer writer) throws IOException {
        List<RawLocationPoint> points = rawLocationPointRepository.findByUserAndTimestampBetweenOrderByTimestampAsc(
                user,
                start,
                end,
                false);

        FeatureCollection collection = new FeatureCollection();
        for (RawLocationPoint point : points) {
            Feature feature = new Feature();
            feature.setGeometry(createPointGeometry(point.getGeom(), point.getElevationMeters()));
            feature.setProperties(createProperties(point.getId(), point.getTimestamp(), point.getAccuracyMeters(), point.getElevationMeters(), null, point.getSourceId()));
            collection.getFeatures().add(feature);
        }

        objectMapper.writeValue(writer, collection);
    }

    public void generateGeoJsonContentStreaming(User user,
                                                Instant start,
                                                Instant end,
                                                Long deviceId,
                                                Writer writer) throws IOException {
        Device device = this.deviceJdbcService.find(user, deviceId).orElse(null);
        List<SourceLocationPoint> points = sourceLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(
                user,
                device,
                start,
                end,
                true, true);

        FeatureCollection collection = new FeatureCollection();
        for (SourceLocationPoint point : points) {
            Feature feature = new Feature();
            feature.setGeometry(createPointGeometry(point.getGeom(), point.getElevationMeters()));
            feature.setProperties(createProperties(point.getId(), point.getTimestamp(), point.getAccuracyMeters(), point.getElevationMeters(), device != null ? device.id() : null, point.getId()));
            collection.getFeatures().add(feature);
        }

        objectMapper.writeValue(writer, collection);
    }

    private Geometry createPointGeometry(GeoPoint point, Double elevationMeters) {
        List<Double> coordinates = new ArrayList<>();
        coordinates.add(point.longitude());
        coordinates.add(point.latitude());
        if (elevationMeters != null) {
            coordinates.add(elevationMeters);
        }
        Geometry geometry = new Geometry();
        geometry.setType("Point");
        geometry.setCoordinates(coordinates);
        return geometry;
    }

    private Map<String, Object> createProperties(Long id, Instant timestamp, Double accuracyMeters, Double elevationMeters, Long deviceId, Long sourceId) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", id);
        props.put("timestamp", timestamp.toString());
        props.put("accuracy", accuracyMeters);
        props.put("elevation", elevationMeters);
        if (deviceId != null) {
            props.put("device", deviceId);
        }
        if (sourceId != null) {
            props.put("sourceId", sourceId);
        }
        return props;
    }

    static class FeatureCollection {
        private String type = "FeatureCollection";
        private List<Feature> features = new ArrayList<>();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<Feature> getFeatures() {
            return features;
        }

        public void setFeatures(List<Feature> features) {
            this.features = features;
        }
    }

    static class Feature {
        private String type = "Feature";
        private Geometry geometry;
        private Map<String, Object> properties;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Geometry getGeometry() {
            return geometry;
        }

        public void setGeometry(Geometry geometry) {
            this.geometry = geometry;
        }

        public Map<String, Object> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, Object> properties) {
            this.properties = properties;
        }
    }

    static class Geometry {
        private String type;
        private List<Double> coordinates;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<Double> getCoordinates() {
            return coordinates;
        }

        public void setCoordinates(List<Double> coordinates) {
            this.coordinates = coordinates;
        }
    }
}
