package com.dedicatedcode.reitti.model.memory;

import com.dedicatedcode.reitti.service.TimeUtil;

import java.sql.Time;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class MemoryDTO {
    private final Long id;
    private final String title;
    private final String description;
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;
    private final HeaderType headerType;
    private final String headerImageUrl;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Long version;
    private final ZoneId timezone;

    public MemoryDTO(Memory memory, ZoneId timezone) {
        this.id = memory.getId();
        this.title = memory.getTitle();
        this.description = memory.getDescription();
        this.startDate = TimeUtil.adjustInstant(memory.getStartDate(), timezone);
        this.endDate = TimeUtil.adjustInstant(memory.getEndDate(), timezone);
        this.headerType = memory.getHeaderType();
        this.headerImageUrl = memory.getHeaderImageUrl();
        this.createdAt = memory.getCreatedAt();
        this.updatedAt = memory.getUpdatedAt();
        this.version = memory.getVersion();
        this.timezone = timezone;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public HeaderType getHeaderType() {
        return headerType;
    }

    public String getHeaderImageUrl() {
        return headerImageUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public ZoneId getTimezone() {
        return timezone;
    }

    public Instant getStartDateAsInstant() {
        return startDate.atZone(timezone).toInstant();
    }

    public Instant getEndDateAsInstant() {
        return endDate.atZone(timezone).toInstant();
    }
}
