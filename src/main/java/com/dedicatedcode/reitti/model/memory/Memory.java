package com.dedicatedcode.reitti.model.memory;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

public class Memory implements Serializable {
    
    private final Long id;
    private final String title;
    private final String description;
    private final Instant startDate;
    private final Instant endDate;
    private final HeaderType headerType;
    private final String headerImageUrl;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Long version;

    public Memory(String title, String description, Instant startDate, Instant endDate, HeaderType headerType, String headerImageUrl) {
        this(null, title, description, startDate, endDate, headerType, headerImageUrl, Instant.now(), Instant.now(), 1L);
    }

    public Memory(Long id, String title, String description, Instant startDate, Instant endDate, HeaderType headerType, String headerImageUrl, Instant createdAt, Instant updatedAt, Long version) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.headerType = headerType;
        this.headerImageUrl = headerImageUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
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

    public Instant getStartDate() {
        return startDate;
    }

    public Instant getEndDate() {
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

    public Memory withId(Long id) {
        return new Memory(id, this.title, this.description, this.startDate, this.endDate, this.headerType, this.headerImageUrl, this.createdAt, this.updatedAt, this.version);
    }

    public Memory withTitle(String title) {
        return new Memory(this.id, title, this.description, this.startDate, this.endDate, this.headerType, this.headerImageUrl, this.createdAt, Instant.now(), this.version);
    }

    public Memory withDescription(String description) {
        return new Memory(this.id, this.title, description, this.startDate, this.endDate, this.headerType, this.headerImageUrl, this.createdAt, Instant.now(), this.version);
    }

    public Memory withStartDate(Instant startDate) {
        return new Memory(this.id, this.title, this.description, startDate, this.endDate, this.headerType, this.headerImageUrl, this.createdAt, Instant.now(), this.version);
    }

    public Memory withEndDate(Instant endDate) {
        return new Memory(this.id, this.title, this.description, this.startDate, endDate, this.headerType, this.headerImageUrl, this.createdAt, Instant.now(), this.version);
    }

    public Memory withHeaderType(HeaderType headerType) {
        return new Memory(this.id, this.title, this.description, this.startDate, this.endDate, headerType, this.headerImageUrl, this.createdAt, Instant.now(), this.version);
    }

    public Memory withHeaderImageUrl(String headerImageUrl) {
        return new Memory(this.id, this.title, this.description, this.startDate, this.endDate, this.headerType, headerImageUrl, this.createdAt, Instant.now(), this.version);
    }

    public Memory withVersion(Long version) {
        return new Memory(this.id, this.title, this.description, this.startDate, this.endDate, this.headerType, this.headerImageUrl, this.createdAt, this.updatedAt, version);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Memory memory = (Memory) o;

        return id != null ? id.equals(memory.id) : memory.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Memory{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", headerType=" + headerType +
                ", headerImageUrl='" + headerImageUrl + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", version=" + version +
                '}';
    }
}
