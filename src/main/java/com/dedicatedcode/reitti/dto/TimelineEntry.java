package com.dedicatedcode.reitti.dto;

import com.dedicatedcode.reitti.controller.TimelineController;
import com.dedicatedcode.reitti.model.SignificantPlace;

import java.time.Instant;

/**
 * Inner class to represent timeline entries for the template
 */
public class TimelineEntry {
    public enum Type {VISIT, TRIP}

    private String id;
    private Type type;
    private SignificantPlace place;
    private String path;
    private Instant startTime;
    private Instant endTime;
    private String formattedTimeRange;
    private String formattedDuration;
    private Double distanceMeters;
    private String formattedDistance;
    private String transportMode;

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public SignificantPlace getPlace() {
        return place;
    }

    public void setPlace(SignificantPlace place) {
        this.place = place;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public String getFormattedTimeRange() {
        return formattedTimeRange;
    }

    public void setFormattedTimeRange(String formattedTimeRange) {
        this.formattedTimeRange = formattedTimeRange;
    }

    public String getFormattedDuration() {
        return formattedDuration;
    }

    public void setFormattedDuration(String formattedDuration) {
        this.formattedDuration = formattedDuration;
    }

    public Double getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(Double distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public String getFormattedDistance() {
        return formattedDistance;
    }

    public void setFormattedDistance(String formattedDistance) {
        this.formattedDistance = formattedDistance;
    }

    public String getTransportMode() {
        return transportMode;
    }

    public void setTransportMode(String transportMode) {
        this.transportMode = transportMode;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

}
