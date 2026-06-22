package com.dedicatedcode.reitti.service.processing;

import java.io.Serializable;
import java.time.Instant;

public record TimeRange(Instant start, Instant end) implements Serializable {
    public static TimeRange empty() {
        return new TimeRange(null, null);
    }

    public static TimeRange of(Instant start, Instant end) {
        return new TimeRange(start, end);
    }

    public TimeRange extend(TimeRange other) {
        if (this.equals(empty())) {
            return other;
        } else if (other.equals(empty())) {
            return this;
        }
        Instant start = this.start == null ? other.start : this.start.isBefore(other.start) ? this.start : other.start;
        Instant end = this.end == null ? other.end : this.end.isAfter(other.end) ? this.end : other.end;
        return new TimeRange(start, end);
    }
}
