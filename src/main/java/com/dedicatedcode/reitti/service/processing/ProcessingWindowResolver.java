package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.processing.DetectionParameter;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import com.dedicatedcode.reitti.service.VisitDetectionParametersService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class ProcessingWindowResolver {

    private static final Logger log = LoggerFactory.getLogger(ProcessingWindowResolver.class);

    private final SourceLocationPointJdbcService sourceLocationPointJdbcService;
    private final VisitDetectionParametersService visitDetectionParametersService;
    private final GeoPointAnomalyFilterConfig filterConfig;

    public ProcessingWindowResolver(SourceLocationPointJdbcService sourceLocationPointJdbcService,
                                    VisitDetectionParametersService visitDetectionParametersService,
                                    GeoPointAnomalyFilterConfig filterConfig) {
        this.sourceLocationPointJdbcService = sourceLocationPointJdbcService;
        this.visitDetectionParametersService = visitDetectionParametersService;
        this.filterConfig = filterConfig;
    }

    public TimeRange resolve(User user, Device device, TimeRange promotedRange) {
        DetectionParameter params = visitDetectionParametersService.getCurrentConfiguration(user, promotedRange.start());
        Duration cap = Duration.ofMinutes(params.getLocationDensity().getMaxInterpolationGapMinutes());
        int contextPoints = this.filterConfig.getWindowSize();

        Instant windowStart = promotedRange.start();
        Instant windowEnd = promotedRange.end();

        Optional<Instant> before = this.sourceLocationPointJdbcService
                .findNthPointTimestampBefore(user, device, windowStart, contextPoints);
        if (before.isPresent()) {
            Duration back = Duration.between(before.get(), windowStart);
            windowStart = back.compareTo(cap) > 0 ? windowStart.minus(cap) : before.get();
        }

        Optional<Instant> after = this.sourceLocationPointJdbcService
                .findNthPointTimestampAfter(user, device, windowEnd, contextPoints);
        if (after.isPresent()) {
            Duration forward = Duration.between(windowEnd, after.get());
            windowEnd = forward.compareTo(cap) > 0 ? windowEnd.plus(cap) : after.get();
        }

        if (!windowEnd.isAfter(promotedRange.end())) {
            windowEnd = promotedRange.end().plus(1, ChronoUnit.MILLIS);
        }

        log.debug("Resolved processing window [{}] for promoted range [{}]", new TimeRange(windowStart, windowEnd), promotedRange);
        return new TimeRange(windowStart, windowEnd);
    }
}
