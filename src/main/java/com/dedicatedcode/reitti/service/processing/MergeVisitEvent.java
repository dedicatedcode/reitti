package com.dedicatedcode.reitti.service.processing;

import java.io.Serializable;
import java.time.Instant;

public record MergeVisitEvent(Long userId, Instant startTime, Instant endTime) implements Serializable {
}
