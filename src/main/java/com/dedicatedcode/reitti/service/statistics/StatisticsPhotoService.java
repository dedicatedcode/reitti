package com.dedicatedcode.reitti.service.statistics;

import com.dedicatedcode.reitti.dto.PhotoResponse;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.integration.ImmichIntegrationService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class StatisticsPhotoService {

    private final ImmichIntegrationService immichIntegrationService;

    public StatisticsPhotoService(ImmichIntegrationService immichIntegrationService) {
        this.immichIntegrationService = immichIntegrationService;
    }

    public long countStaysWithoutPhotos(User user, List<StatisticsReport.StayWindow> stays) {
        if (stays.isEmpty()) {
            return 0;
        }
        Instant start = stays.stream().map(StatisticsReport.StayWindow::start).min(Comparator.naturalOrder()).orElseThrow();
        Instant end = stays.stream().map(StatisticsReport.StayWindow::end).max(Comparator.naturalOrder()).orElseThrow();
        List<Instant> photoTimes = immichIntegrationService.searchPhotosForRange(user, start, end).stream()
                .map(PhotoResponse::getDateTime)
                .map(StatisticsPhotoService::parse)
                .filter(Objects::nonNull)
                .toList();
        return stays.stream()
                .filter(stay -> photoTimes.stream().noneMatch(t -> !t.isBefore(stay.start()) && !t.isAfter(stay.end())))
                .count();
    }

    private static Instant parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }
}
