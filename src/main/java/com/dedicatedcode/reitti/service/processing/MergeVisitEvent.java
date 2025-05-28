package com.dedicatedcode.reitti.service.processing;

import org.springframework.messaging.core.MessagePostProcessor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

public record MergeVisitEvent(Long userId, Instant startTime, Instant endTime) implements Serializable {
}
