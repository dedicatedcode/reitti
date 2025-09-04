package com.dedicatedcode.reitti.model;

import com.dedicatedcode.reitti.event.SSEType;

import java.time.LocalDate;
import java.util.Set;

public class NotificationData {
    private final SSEType eventType;
    private final Long userId;
    private final Set<LocalDate> affectedDates;

    public NotificationData(SSEType eventType, Long userId, Set<LocalDate> affectedDates) {
        this.eventType = eventType;
        this.userId = userId;
        this.affectedDates = affectedDates;
    }

    public SSEType getEventType() {
        return eventType;
    }

    public Long getUserId() {
        return userId;
    }

    public Set<LocalDate> getAffectedDates() {
        return affectedDates;
    }
}
