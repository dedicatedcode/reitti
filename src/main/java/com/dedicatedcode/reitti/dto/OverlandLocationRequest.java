package com.dedicatedcode.reitti.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class OverlandLocationRequest {
    private static final Logger log = LoggerFactory.getLogger(OverlandLocationRequest.class);

    @JsonProperty("locations")
    private List<OverlandLocation> locations;
    
    @JsonProperty("current")
    private OverlandLocation current;
    
    @JsonProperty("trip")
    private Map<String, Object> trip;
    
    public List<OverlandLocation> getLocations() {
        return locations;
    }
    
    public void setLocations(List<OverlandLocation> locations) {
        this.locations = locations;
    }
    
    public OverlandLocation getCurrent() {
        return current;
    }
    
    public void setCurrent(OverlandLocation current) {
        this.current = current;
    }
    
    public Map<String, Object> getTrip() {
        return trip;
    }
    
    public void setTrip(Map<String, Object> trip) {
        this.trip = trip;
    }
    
    public static class OverlandLocation {
        @JsonProperty("type")
        private String type;
        
        @JsonProperty("geometry")
        private Geometry geometry;
        
        @JsonProperty("properties")
        private Properties properties;
        
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
        
        public Properties getProperties() {
            return properties;
        }
        
        public void setProperties(Properties properties) {
            this.properties = properties;
        }
        
        public LocationPoint toLocationPoint() {
            if (geometry == null || geometry.getCoordinates() == null || 
                geometry.getCoordinates().size() < 2 || properties == null) {
                return null;
            }
            
            double longitude = geometry.getCoordinates().get(0);
            double latitude = geometry.getCoordinates().get(1);

            String timestamp = null;
            if (properties.getTimestamp() != null) {
                try {
                    timestamp = Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(properties.getTimestamp())).toString();
                } catch (Exception e) {
                    // Try parsing as ISO instant
                    try {
                        timestamp = Instant.parse(properties.getTimestamp()).toString();
                    } catch (Exception ex) {
                        log.warn("Could not parse timestamp [{}]", properties.getTimestamp());
                    }
                }
            }

            LocationPoint locationPoint = new LocationPoint();
            locationPoint.setLatitude(latitude);
            locationPoint.setLongitude(longitude);
            locationPoint.setElevationMeters(properties.getAltitude());
            locationPoint.setAccuracyMeters(properties.getHorizontalAccuracy());
            locationPoint.setTimestamp(timestamp);
            return locationPoint;
        }
    }
    
    public static class Geometry {
        @JsonProperty("type")
        private String type;
        
        @JsonProperty("coordinates")
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
    
    public static class Properties {
        @JsonProperty("timestamp")
        private String timestamp;
        
        @JsonProperty("altitude")
        private Double altitude;
        
        @JsonProperty("speed")
        private Double speed;
        
        @JsonProperty("horizontal_accuracy")
        private Double horizontalAccuracy;
        
        @JsonProperty("vertical_accuracy")
        private Double verticalAccuracy;
        
        @JsonProperty("motion")
        private List<String> motion;
        
        @JsonProperty("battery_state")
        private String batteryState;
        
        @JsonProperty("battery_level")
        private Double batteryLevel;
        
        @JsonProperty("device_id")
        private String deviceId;
        
        @JsonProperty("wifi")
        private String wifi;
        
        public String getTimestamp() {
            return timestamp;
        }
        
        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }
        
        public Double getAltitude() {
            return altitude;
        }
        
        public void setAltitude(Double altitude) {
            this.altitude = altitude;
        }
        
        public Double getSpeed() {
            return speed;
        }
        
        public void setSpeed(Double speed) {
            this.speed = speed;
        }
        
        public Double getHorizontalAccuracy() {
            return horizontalAccuracy;
        }
        
        public void setHorizontalAccuracy(Double horizontalAccuracy) {
            this.horizontalAccuracy = horizontalAccuracy;
        }
        
        public Double getVerticalAccuracy() {
            return verticalAccuracy;
        }
        
        public void setVerticalAccuracy(Double verticalAccuracy) {
            this.verticalAccuracy = verticalAccuracy;
        }
        
        public List<String> getMotion() {
            return motion;
        }
        
        public void setMotion(List<String> motion) {
            this.motion = motion;
        }
        
        public String getBatteryState() {
            return batteryState;
        }
        
        public void setBatteryState(String batteryState) {
            this.batteryState = batteryState;
        }
        
        public Double getBatteryLevel() {
            return batteryLevel;
        }
        
        public void setBatteryLevel(Double batteryLevel) {
            this.batteryLevel = batteryLevel;
        }
        
        public String getDeviceId() {
            return deviceId;
        }
        
        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }
        
        public String getWifi() {
            return wifi;
        }
        
        public void setWifi(String wifi) {
            this.wifi = wifi;
        }
    }
}
