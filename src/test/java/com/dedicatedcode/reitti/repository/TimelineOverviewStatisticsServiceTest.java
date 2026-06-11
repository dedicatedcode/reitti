package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.dto.timeline.GroupedTimelineEntry;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.metadata.Mood;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Timestamp;
import java.time.*;
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
    private Long placeId;

    @BeforeEach
    void setUp() {
        testingService.clearData();
        user = testingService.randomUser();
        SignificantPlace place = testingService.newSignificantPlace(user);
        placeId = place.getId();
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

            jdbcTemplate.update(
                    "INSERT INTO trips (user_id, start_time, end_time, duration_seconds, travelled_distance_meters, transport_mode_inferred, start_visit_id, end_visit_id, estimated_distance_meters) " +
                            "VALUES (:userId, :start, :end, :duration, :distance, :mode, NULL, NULL, :distance)",
                    Map.of(
                            "userId", user.getId(),
                            "start", Timestamp.from(dayStart),
                            "end", Timestamp.from(dayEnd),
                            "duration", 3600L,
                            "distance", 5000L,
                            "mode", TransportMode.WALKING.name()
                    )
            );

            jdbcTemplate.update(
                    "INSERT INTO processed_visits (user_id, place_id, start_time, end_time, duration_seconds, version, metadata) " +
                            "VALUES (:userId, :placeId, :start, :end, :duration, 1, '{}'::jsonb)",
                    Map.of(
                            "userId", user.getId(),
                            "placeId", placeId,
                            "start", Timestamp.from(dayStart),
                            "end", Timestamp.from(dayEnd),
                            "duration", 7200L
                    )
            );

            jdbcTemplate.update(
                    "INSERT INTO location_metadata (user_id, time_range, metadata) " +
                            "VALUES (:userId, TSTZRANGE(:start, :end), :metadata::jsonb)",
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
        assertEquals(1, firstWeek.overview().get(0).visits());
        assertEquals(1, firstWeek.overview().get(0).trips());
        assertEquals(7, firstWeek.trips());
        assertEquals(7, firstWeek.visits());
        assertFalse(firstWeek.visitMoods().isEmpty());
        assertEquals(Mood.HAPPY, firstWeek.visitMoods().get(0).mood());
        assertFalse(firstWeek.tripMoods().isEmpty());
        assertEquals(Mood.HAPPY, firstWeek.tripMoods().get(0).mood());
        assertFalse(firstWeek.transportEntries().isEmpty());
        GroupedTimelineEntry.TransportEntry transport = firstWeek.transportEntries().get(0);
        assertEquals(TransportMode.WALKING, transport.transportMode());
        assertTrue(transport.durationSeconds() > 0);
        assertFalse(transport.parts().isEmpty());
        GroupedTimelineEntry.TransportModePart part = transport.parts().get(0);
        assertEquals(TransportMode.WALKING, part.transportMode());
        assertEquals(Mood.HAPPY, part.mood());
        assertTrue(part.percent() >= 0.0 && part.percent() <= 1.0);
        assertFalse(firstWeek.visitEntries().isEmpty());
        GroupedTimelineEntry.VisitEntry visit = firstWeek.visitEntries().get(0);
        assertNotNull(visit.name());
        assertTrue(visit.durationSeconds() > 0);
        assertFalse(visit.parts().isEmpty());
        GroupedTimelineEntry.VisitPart visitPart = visit.parts().get(0);
        assertEquals(placeId, visitPart.placeId());
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

            jdbcTemplate.update(
                    "INSERT INTO trips (user_id, start_time, end_time, duration_seconds, travelled_distance_meters, transport_mode_inferred, start_visit_id, end_visit_id, estimated_distance_meters) " +
                            "VALUES (:userId, :start, :end, :duration, :distance, :mode, NULL, NULL, :distance)",
                    Map.of(
                            "userId", user.getId(),
                            "start", Timestamp.from(dayStart),
                            "end", Timestamp.from(dayEnd),
                            "duration", 1800L,
                            "distance", 2000L,
                            "mode", TransportMode.CYCLING.name()
                    )
            );

            jdbcTemplate.update(
                    "INSERT INTO processed_visits (user_id, place_id, start_time, end_time, duration_seconds, version, metadata) " +
                            "VALUES (:userId, :placeId, :start, :end, :duration, 1, '{}'::jsonb)",
                    Map.of(
                            "userId", user.getId(),
                            "placeId", placeId,
                            "start", Timestamp.from(dayStart),
                            "end", Timestamp.from(dayEnd),
                            "duration", 5400L
                    )
            );

            jdbcTemplate.update(
                    "INSERT INTO location_metadata (user_id, time_range, metadata) " +
                            "VALUES (:userId, TSTZRANGE(:start, :end), :metadata::jsonb)",
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
        assertEquals(1, janEntry.visits());
        assertFalse(janEntry.visitMoods().isEmpty());
        assertEquals(Mood.STRESSED, janEntry.visitMoods().get(0).mood());
        assertFalse(janEntry.tripMoods().isEmpty());
        assertEquals(Mood.STRESSED, janEntry.tripMoods().get(0).mood());
        assertFalse(janEntry.transportEntries().isEmpty());
        GroupedTimelineEntry.TransportEntry transport = janEntry.transportEntries().get(0);
        assertEquals(TransportMode.CYCLING, transport.transportMode());
        assertEquals(1800L, transport.durationSeconds());
        assertEquals(1, transport.parts().size());
        assertEquals(1.0, transport.parts().get(0).percent(), 0.001);
        assertFalse(janEntry.visitEntries().isEmpty());
        GroupedTimelineEntry.VisitEntry visit = janEntry.visitEntries().get(0);
        assertNotNull(visit.name());
        assertEquals(5400L, visit.durationSeconds());
        assertEquals(1, visit.parts().size());
        assertEquals(1.0, visit.parts().get(0).percent(), 0.001);
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

        jdbcTemplate.update(
                "INSERT INTO trips (user_id, start_time, end_time, duration_seconds, travelled_distance_meters, transport_mode_inferred, start_visit_id, end_visit_id, estimated_distance_meters) " +
                        "VALUES (:userId, :start, :end, :duration, :distance, :mode, NULL, NULL, :distance)",
                Map.of(
                        "userId", user.getId(),
                        "start", Timestamp.from(dayStart),
                        "end", Timestamp.from(dayEnd),
                        "duration", 3600L,
                        "distance", 5000L,
                        "mode", TransportMode.WALKING.name()
                )
        );

        jdbcTemplate.update(
                "INSERT INTO processed_visits (user_id, place_id, start_time, end_time, duration_seconds, version, metadata) " +
                        "VALUES (:userId, :placeId, :start, :end, :duration, 1, '{}'::jsonb)",
                Map.of(
                        "userId", user.getId(),
                        "placeId", placeId,
                        "start", Timestamp.from(dayStart),
                        "end", Timestamp.from(dayEnd),
                        "duration", 7200L
                )
        );

        jdbcTemplate.update(
                "INSERT INTO location_metadata (user_id, time_range, metadata) " +
                        "VALUES (:userId, TSTZRANGE(:start, :end), :metadata::jsonb)",
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
        assertEquals(1, entry.overview().get(0).visits());
        assertEquals(1, entry.overview().get(0).trips());

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
        assertEquals(TransportMode.WALKING, transport.transportMode());
        assertNotNull(transport.parts());
        assertFalse(transport.parts().isEmpty());
        GroupedTimelineEntry.TransportModePart tp = transport.parts().get(0);
        assertEquals(TransportMode.WALKING, tp.transportMode());
        assertEquals(Mood.ADVENTUROUS, tp.mood());
        assertEquals(3600L, tp.durationSeconds());
        assertEquals(1.0, tp.percent(), 0.001);

        assertNotNull(entry.visitEntries());
        assertFalse(entry.visitEntries().isEmpty());
        GroupedTimelineEntry.VisitEntry visitEntry = entry.visitEntries().get(0);
        assertNotNull(visitEntry.name());
        assertNotNull(visitEntry.parts());
        assertFalse(visitEntry.parts().isEmpty());
        GroupedTimelineEntry.VisitPart vp = visitEntry.parts().get(0);
        assertEquals(placeId, vp.placeId());
        assertNotNull(vp.placeName());
        assertEquals(Mood.ADVENTUROUS, vp.mood());
        assertEquals(7200L, vp.durationSeconds());
        assertEquals(1.0, vp.percent(), 0.001);
    }
}