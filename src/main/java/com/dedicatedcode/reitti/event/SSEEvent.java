package com.dedicatedcode.reitti.event;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.io.Serializable;
import java.time.LocalDate;

public class SSEEvent implements Serializable {
    private final SSEType type;
    private final Long userId;
    private final Long changedUserId;
    private final LocalDate date;

    @JsonCreator
    public SSEEvent(SSEType type, Long userId, Long changedUserId, LocalDate date) {
        this.type = type;
        this.userId = userId;
        this.changedUserId = changedUserId;
        this.date = date;
    }

    public SSEType getType() {
        return type;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getChangedUserId() {
        return changedUserId;
    }

    public LocalDate getDate() {
        return date;
    }
}
