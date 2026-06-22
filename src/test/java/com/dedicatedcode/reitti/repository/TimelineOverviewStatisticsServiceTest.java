package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.dto.timeline.GroupedTimelineEntry;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.metadata.Mood;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class TimelineOverviewStatisticsServiceTest {

    @Autowired
    private TimelineOverviewStatisticsService service;
    @Autowired
    private TestingService testingService;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private User user;
    private SignificantPlace place;

    @BeforeEach
    void setUp() {
        testingService.clearData();
        user = testingService.randomUser();
        place = testingService.newSignificantPlace(user, "Test Place");
        LocaleContextHolder.setLocale(Locale.ENGLISH);

    }

    @Test
    void testWeekGranularity() {
        ZoneId tz = ZoneId.of("Europe/Berlin");
        LocalDate startDate = LocalDate.of(2025, 1, 6);
        LocalDate endDate = startDate.plusDays(13);

        for (int day = 0; day < 14; day++) {
            LocalDate date = startDate.plusDays(day);
            Instant dayStart = date.atStartOfDay(tz).toInstant();
            Instant dayEnd = date.atTime(LocalTime.of(23, 59)).atZone(tz).toInstant();

            ProcessedVisit visit = testingService.createVisit(user, place, dayStart.plus(5, ChronoUnit.HOURS), dayStart.plus(7, ChronoUnit.HOURS));
            ProcessedVisit visit1 = testingService.createVisit(user, place, dayStart.plus(16, ChronoUnit.HOURS), dayEnd);

            testingService.createTrip(user, visit, visit1, TransportMode.WALKING);

            jdbcTemplate.update(
                    "INSERT INTO location_metadata (user_id, context_type, time_range, metadata) " +
                            "VALUES (:userId, 'VISIT', TSTZRANGE(:start, :end), :metadata::jsonb)",
                    Map.of(
                            "userId", user.getId(),
                            "start", Timestamp.from(dayStart),
                            "end", Timestamp.from(dayEnd),
                            "metadata", String.format("{\"mood\":\"%s\"}", Mood.HAPPY.name())
                    )
            );
        }

        List<GroupedTimelineEntry> entries = service.load(user, startDate.atStartOfDay(tz).toInstant(), endDate.atTime(LocalTime.MAX).atZone(tz).toInstant(), tz);

        assertNotNull(entries);
        assertFalse(entries.isEmpty());
        assertEquals(2, entries.size());

        GroupedTimelineEntry firstWeek = entries.get(0);
        assertTrue(firstWeek.name().contains("Week"));
        assertNotNull(firstWeek.subHeadline());
        assertTrue(firstWeek.href().contains("/?startDate="));
        assertEquals(7, firstWeek.overview().size());
        assertEquals(2, firstWeek.overview().get(0).visits());
        assertEquals(1, firstWeek.overview().get(0).trips());
        assertEquals(7, firstWeek.trips());
        assertEquals(14, firstWeek.visits());
        assertFalse(firstWeek.visitMoods().isEmpty());
        assertEquals(Mood.HAPPY, firstWeek.visitMoods().get(0).mood());
        assertFalse(firstWeek.tripMoods().isEmpty());
        assertNull(firstWeek.tripMoods().get(0).mood());
        assertFalse(firstWeek.transportEntries().isEmpty());
        GroupedTimelineEntry.TransportEntry transport = firstWeek.transportEntries().get(0);
        assertEquals(TransportMode.WALKING, transport.transportMode());
        assertTrue(transport.durationSeconds() > 0);
        assertFalse(transport.parts().isEmpty());
        GroupedTimelineEntry.TransportModePart part = transport.parts().get(0);
        assertEquals(TransportMode.WALKING, part.transportMode());
        assertNull(part.mood());
        assertTrue(part.percent() >= 0.0 && part.percent() <= 1.0);
        assertFalse(firstWeek.visitEntries().isEmpty());
        GroupedTimelineEntry.VisitEntry visit = firstWeek.visitEntries().get(0);
        assertTrue(visit.durationSeconds() > 0);
        assertFalse(visit.parts().isEmpty());
        GroupedTimelineEntry.VisitPart visitPart = visit.parts().get(0);
        assertEquals(place.getId(), visitPart.placeId());
        assertEquals(Mood.HAPPY, visitPart.mood());
        assertTrue(visitPart.percent() >= 0.0 && visitPart.percent() <= 1.0);
    }

    @Test
    void testMonthGranularity() {
        ZoneId tz = ZoneId.of("UTC");
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = startDate.plusMonths(12);

        for (int month = 0; month < 3; month++) {
            LocalDate date = startDate.plusMonths(month).withDayOfMonth(15);
            Instant dayStart = date.atStartOfDay(tz).toInstant();
            Instant dayEnd = date.atTime(LocalTime.of(23, 59)).atZone(tz).toInstant();

            ProcessedVisit visit = testingService.createVisit(user, place, dayStart, dayStart.plus(7, ChronoUnit.HOURS));
            ProcessedVisit visit2 = testingService.createVisit(user, place, dayStart.plus(8, ChronoUnit.HOURS), dayEnd);

            testingService.createTrip(user, visit, visit2, TransportMode.CYCLING);
            // Insert mood metadata
            jdbcTemplate.update(
                    "INSERT INTO location_metadata (user_id, context_type, time_range, metadata) " +
                            "VALUES (:userId, 'TRIP', TSTZRANGE(:start, :end), :metadata::jsonb)",
                    Map.of(
                            "userId", user.getId(),
                            "start", Timestamp.from(dayStart),
                            "end", Timestamp.from(dayEnd),
                            "metadata", String.format("{\"mood\":\"%s\"}", Mood.STRESSED.name())
                    )
            );
            jdbcTemplate.update(
                    "INSERT INTO location_metadata (user_id, context_type, time_range, metadata) " +
                            "VALUES (:userId, 'VISIT', TSTZRANGE(:start, :end), :metadata::jsonb)",
                    Map.of(
                            "userId", user.getId(),
                            "start", Timestamp.from(dayStart),
                            "end", Timestamp.from(dayEnd),
                            "metadata", String.format("{\"mood\":\"%s\"}", Mood.STRESSED.name())
                    )
            );
        }

        List<GroupedTimelineEntry> entries = service.load(user, startDate.atStartOfDay(tz).toInstant(), endDate.atTime(LocalTime.MAX).atZone(tz).toInstant(), tz);

        assertNotNull(entries);
        assertFalse(entries.isEmpty());
        assertEquals(3, entries.size());

        GroupedTimelineEntry janEntry = entries.get(0);
        assertTrue(janEntry.name().contains("January"));
        assertTrue(janEntry.subHeadline().contains("01."));
        assertEquals(31, janEntry.overview().size());
        long daysWithVisits = janEntry.overview().stream().filter(e -> e.visits() > 0).count();
        assertEquals(1, daysWithVisits);
        assertEquals(1, janEntry.trips());
        assertEquals(2, janEntry.visits());
        assertFalse(janEntry.visitMoods().isEmpty());
        assertEquals(Mood.STRESSED, janEntry.visitMoods().get(0).mood());
        assertFalse(janEntry.tripMoods().isEmpty());
        assertEquals(Mood.STRESSED, janEntry.tripMoods().get(0).mood());
        assertFalse(janEntry.transportEntries().isEmpty());
        GroupedTimelineEntry.TransportEntry transport = janEntry.transportEntries().get(0);
        assertEquals(TransportMode.CYCLING, transport.transportMode());
        assertEquals(3600L, transport.durationSeconds());
        assertEquals(1, transport.parts().size());
        assertEquals(1.0, transport.parts().get(0).percent(), 0.001);
        assertFalse(janEntry.visitEntries().isEmpty());
        GroupedTimelineEntry.VisitEntry visit = janEntry.visitEntries().get(0);
        assertNotNull(visit.name());
        assertEquals(82740L, visit.durationSeconds());
        assertEquals(2, visit.parts().size());
        assertEquals(0.3045, visit.parts().get(0).percent(), 0.001);
    }

    @Test
    void testEmptyDataReturnsEmptyList() {
        ZoneId tz = ZoneId.systemDefault();
        Instant start = Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS);
        Instant end = Instant.now();
        List<GroupedTimelineEntry> entries = service.load(user, start, end, tz);
        assertNotNull(entries);
        assertTrue(entries.isEmpty());
    }

    @Test
    void testAllPartTypesArePresentInResult() {
        ZoneId tz = ZoneId.of("Europe/Berlin");
        LocalDate startDate = LocalDate.of(2025, 6, 2);
        LocalDate endDate = startDate.plusDays(6);
        Instant dayStart = startDate.atStartOfDay(tz).toInstant();
        Instant dayEnd = startDate.atTime(LocalTime.of(23, 59)).atZone(tz).toInstant();

        ProcessedVisit visit1 = testingService.createVisit(user, place, dayStart.plus(10, ChronoUnit.HOURS), dayStart.plus(11, ChronoUnit.HOURS));
        ProcessedVisit visit2 = testingService.createVisit(user, place, dayStart.plus(14, ChronoUnit.HOURS), dayStart.plus(15, ChronoUnit.HOURS));
        ProcessedVisit visit3 = testingService.createVisit(user, place, dayStart.plus(16, ChronoUnit.HOURS), dayStart.plus(20, ChronoUnit.HOURS));

        testingService.createTrip(user, visit1, visit2);
        testingService.createTrip(user, visit2, visit3);

        jdbcTemplate.update(
                "INSERT INTO location_metadata (user_id, context_type, time_range, metadata) " +
                        "VALUES (:userId, 'VISIT', TSTZRANGE(:start, :end), :metadata::jsonb)",
                Map.of(
                        "userId", user.getId(),
                        "start", Timestamp.from(dayStart),
                        "end", Timestamp.from(dayEnd),
                        "metadata", String.format("{\"mood\":\"%s\"}", Mood.ADVENTUROUS.name())
                )
        );
        jdbcTemplate.update(
                "INSERT INTO location_metadata (user_id, context_type, time_range, metadata) " +
                        "VALUES (:userId, 'TRIP', TSTZRANGE(:start, :end), :metadata::jsonb)",
                Map.of(
                        "userId", user.getId(),
                        "start", Timestamp.from(dayStart),
                        "end", Timestamp.from(dayEnd),
                        "metadata", String.format("{\"mood\":\"%s\"}", Mood.ADVENTUROUS.name())
                )
        );

        List<GroupedTimelineEntry> entries = service.load(user, dayStart, dayEnd.plus(1, java.time.temporal.ChronoUnit.SECONDS), tz);

        assertNotNull(entries);
        assertEquals(1, entries.size());
        GroupedTimelineEntry entry = entries.get(0);

        assertNotNull(entry.overview());
        assertFalse(entry.overview().isEmpty());
        assertEquals(startDate, entry.overview().get(0).slot());
        assertEquals(3, entry.overview().get(0).visits());
        assertEquals(2, entry.overview().get(0).trips());

        assertNotNull(entry.visitMoods());
        assertFalse(entry.visitMoods().isEmpty());
        GroupedTimelineEntry.MoodValue visitMood = entry.visitMoods().get(0);
        assertEquals(Mood.ADVENTUROUS, visitMood.mood());
        assertTrue(visitMood.amount() > 0);
        assertTrue(visitMood.durationSeconds() > 0);

        assertNotNull(entry.tripMoods());
        assertFalse(entry.tripMoods().isEmpty());
        GroupedTimelineEntry.MoodValue tripMood = entry.tripMoods().get(0);
        assertEquals(Mood.ADVENTUROUS, tripMood.mood());

        assertNotNull(entry.transportEntries());
        assertFalse(entry.transportEntries().isEmpty());
        GroupedTimelineEntry.TransportEntry transport = entry.transportEntries().get(0);
        assertEquals(TransportMode.UNKNOWN, transport.transportMode()); // default from createTrip
        assertNotNull(transport.parts());
        assertFalse(transport.parts().isEmpty());
        GroupedTimelineEntry.TransportModePart tp = transport.parts().get(0);
        assertEquals(TransportMode.UNKNOWN, tp.transportMode());
        assertEquals(Mood.ADVENTUROUS, tp.mood());
        assertEquals(14400, tp.durationSeconds());
        assertEquals(1.0, tp.percent(), 0.001);

        assertNotNull(entry.visitEntries());
        assertFalse(entry.visitEntries().isEmpty());
        GroupedTimelineEntry.VisitEntry visitEntry = entry.visitEntries().get(0);
        assertNotNull(visitEntry.name());
        assertNotNull(visitEntry.parts());
        assertFalse(visitEntry.parts().isEmpty());
        assertEquals(3, visitEntry.parts().size());
        GroupedTimelineEntry.VisitPart vp = visitEntry.parts().get(0);
        assertEquals(place.getId(), vp.placeId());
        assertNotNull(vp.placeName());
        assertEquals(Mood.ADVENTUROUS, vp.mood());
        assertEquals(3600L, vp.durationSeconds());
        assertEquals(0.166, vp.percent(), 0.001);
    }
}