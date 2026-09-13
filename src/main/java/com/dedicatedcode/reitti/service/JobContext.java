package com.dedicatedcode.reitti.service;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public abstract class JobContext<T> {
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .build();

    protected final UUID jobId;
    protected final UUID parentJobId;

    protected JobContext() {
        this(null, null);
    }
    protected JobContext(UUID jobId, UUID parentJobId) {
        this.jobId = jobId;
        this.parentJobId = parentJobId;
    }

    public abstract T withJobId(UUID jobId);
    public abstract T withParentJobId(UUID parentJobId);

    public UUID getJobId() {
        return this.jobId;
    }

    public UUID getParentJobId() {
        return this.parentJobId;
    }

    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize " + getClass().getSimpleName() + " to JSON", e);
        }
    }

    public static <T extends JobContext<T>> T fromJson(String json, Class<T> type) {
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to deserialize " + type.getSimpleName() + " from JSON", e);
        }
    }
}
