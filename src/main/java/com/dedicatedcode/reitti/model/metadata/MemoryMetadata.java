package com.dedicatedcode.reitti.model.metadata;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MemoryMetadata {

    private final Map<String, Object> properties = new HashMap<>();
    private final Instant startTime;
    private final Instant endTime;

    public MemoryMetadata(Instant startTime, Instant endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    // --- Time Envelope Getters/Setters ---
    public Instant getStartTime() { return startTime; }

    public Instant getEndTime() { return endTime; }

    public Mood getMood() {
        Object val = properties.get("mood");
        return val == null ? null : Mood.fromString(val.toString());
    }

    public void setMood(Mood mood) {
        if (mood == null) properties.remove("mood");
        else properties.put("mood", mood.name());
    }

    public String getDescription() { return (String) properties.get("description"); }
    public void setDescription(String d) {
        if (d == null) properties.remove("description");
        else properties.put("description", d);
    }

    public String getReason() { return (String) properties.get("reason"); }
    public void setReason(String r) {
        if (r == null) properties.remove("reason");
        else properties.put("reason", r);
    }

    @SuppressWarnings("unchecked")
    public List<String> getTags() {
        Object tags = properties.get("tags");
        if (!(tags instanceof List)) {
            List<String> newList = new ArrayList<>();
            properties.put("tags", newList);
            return newList;
        }
        return (List<String>) tags;
    }

    public void setTags(List<String> tags) {
        if (tags == null) properties.remove("tags");
        else properties.put("tags", tags);
    }

    public Map<String, Object> getProperties() { return properties; }

    public void setProperty(String key, Object value) { this.properties.put(key, value); }

    public void setProperties(Map<String, Object> properties) {
        this.properties.clear();
        this.properties.putAll(properties);
    }

}