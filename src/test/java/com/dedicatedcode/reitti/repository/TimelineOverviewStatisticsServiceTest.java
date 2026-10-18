package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.dto.timeline.GroupedTimelineEntry;
import com.dedicatedcode.reitti.model.UnitSystem;
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

        List<GroupedTimelineEntry> entries = service.load(user, startDate.atStartOfDay(tz).toInstant(), endDate.atTime(LocalTime.MAX).atZone(tz).toInstant(), tz, LocalTime.MIDNIGHT, UnitSystem.METRIC);

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
        assertEquals(1, firstWeek.visits());
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

        assertFalse(firstWeek.monthly());
        assertEquals(0, firstWeek.leadingBlanks());
        assertTrue(firstWeek.overview().stream().allMatch(o -> o.level() == 4));
        assertTrue(firstWeek.overview().stream().allMatch(o -> o.title() != null && o.title().contains("visits")));
        assertEquals("0", firstWeek.formattedDistance());
        assertEquals("km", firstWeek.distanceUnit());

        assertEquals(168, firstWeek.activity().size());
        assertTrue(firstWeek.activity().stream().noneMatch(GroupedTimelineEntry.ActivityCell::blank));
        GroupedTimelineEntry.ActivityCell nightCell = firstWeek.activity().get(0);
        assertEquals(0, nightCell.level());
        assertEquals(LocalDate.of(2025, 1, 6), nightCell.date());
        GroupedTimelineEntry.ActivityCell stayCell = firstWeek.activity().get(5 * 7);
        assertEquals(0, stayCell.level());
        GroupedTimelineEntry.ActivityCell morningCell = firstWeek.activity().get(7 * 7);
        assertEquals(4, morningCell.level());
        assertEquals(LocalDate.of(2025, 1, 6), morningCell.date());
        assertEquals(LocalDate.of(2025, 1, 12), firstWeek.activity().get(6).date());
        assertEquals(LocalDate.of(2025, 1, 12), firstWeek.activity().get(23 * 7 + 6).date());
        assertNotNull(morningCell.title());
        assertTrue(morningCell.title().contains("07:00"));
        assertTrue(morningCell.title().contains("1 h"));

        assertNotNull(firstWeek.movement());
        assertEquals(1, firstWeek.movement().bars().size());
        assertEquals("time", firstWeek.movement().bars().get(0).labelKey());
        assertEquals(1, firstWeek.movement().bars().get(0).segments().size());
        assertEquals("WALKING", firstWeek.movement().bars().get(0).segments().get(0).mode());
        assertEquals(100.0, firstWeek.movement().bars().get(0).segments().get(0).percent(), 0.001);
        assertEquals(1, firstWeek.movement().legend().size());
        assertEquals("WALKING", firstWeek.movement().legend().get(0).mode());
        assertNull(firstWeek.movement().legend().get(0).moodIcon());

        assertEquals(1, firstWeek.topPlaces().size());
        assertEquals("Test Place", firstWeek.topPlaces().get(0).name());
        assertNotNull(firstWeek.topPlaces().get(0).duration());
        assertEquals(place.getLatitudeCentroid(), firstWeek.topPlaces().get(0).latitude());
        assertEquals(place.getLongitudeCentroid(), firstWeek.topPlaces().get(0).longitude());
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

            ProcessedVisit visit = testingService.createVisit(user, place, dayStart.plus(1, ChronoUnit.HOURS), dayStart.plus(7, ChronoUnit.HOURS));
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

        List<GroupedTimelineEntry> entries = service.load(user, startDate.atStartOfDay(tz).toInstant(), endDate.atTime(LocalTime.MAX).atZone(tz).toInstant(), tz, LocalTime.MIDNIGHT, UnitSystem.METRIC);

        assertNotNull(entries);
        assertFalse(entries.isEmpty());
        assertEquals(3, entries.size());

        GroupedTimelineEntry janEntry = entries.get(0);
        assertTrue(janEntry.name().contains("January"));
        assertTrue(janEntry.subHeadline().contains("01."));
        assertEquals(31, janEntry.overview().size());
        assertEquals(LocalDate.of(2024, 1, 1), janEntry.overview().get(0).slot());
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
        assertEquals(3600L, transport.durationSeconds());
        assertEquals(1, transport.parts().size());
        assertEquals(1.0, transport.parts().get(0).percent(), 0.001);
        assertFalse(janEntry.visitEntries().isEmpty());
        GroupedTimelineEntry.VisitEntry visit = janEntry.visitEntries().get(0);
        assertNotNull(visit.name());
        assertEquals(79140L, visit.durationSeconds());
        assertEquals(2, visit.parts().size());
        assertEquals(0.2729, visit.parts().get(0).percent(), 0.001);

        assertTrue(janEntry.monthly());
        assertTrue(janEntry.activity().isEmpty());
        assertEquals(LocalDate.of(2024, 1, 1).getDayOfWeek().getValue() - 1, janEntry.leadingBlanks());
        long maxActivity = janEntry.overview().stream().mapToLong(o -> o.visits() + o.trips()).max().orElseThrow();
        assertEquals(4, janEntry.overview().stream().filter(o -> o.visits() + o.trips() == maxActivity).findFirst().orElseThrow().level());
        assertEquals(0, janEntry.overview().stream().filter(o -> o.visits() + o.trips() == 0).findFirst().orElseThrow().level());

        assertNotNull(janEntry.movement());
        assertEquals(1, janEntry.movement().legend().size());
        assertEquals(Mood.STRESSED.getIcon(), janEntry.movement().legend().get(0).moodIcon());

        assertEquals(1, janEntry.topPlaces().size());
        assertEquals("Test Place", janEntry.topPlaces().get(0).name());
    }

    @Test
    void testEmptyDataReturnsEmptyList() {
        ZoneId tz = ZoneId.systemDefault();
        Instant start = Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS);
        Instant end = Instant.now();
        List<GroupedTimelineEntry> entries = service.load(user, start, end, tz, LocalTime.MIDNIGHT, UnitSystem.METRIC);
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

        List<GroupedTimelineEntry> entries = service.load(user, dayStart, dayEnd.plus(1, java.time.temporal.ChronoUnit.SECONDS), tz, LocalTime.MIDNIGHT, UnitSystem.METRIC);

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

        assertNotNull(entry.movement());
        assertEquals(1, entry.movement().bars().size());
        assertEquals("time", entry.movement().bars().get(0).labelKey());
        assertEquals(1, entry.movement().legend().size());
        assertEquals(Mood.ADVENTUROUS.getIcon(), entry.movement().legend().get(0).moodIcon());
        assertEquals(1, entry.topPlaces().size());
        assertEquals(4, entry.overview().get(0).level());

        assertEquals(168, entry.activity().size());
        assertEquals(144, entry.activity().stream().filter(GroupedTimelineEntry.ActivityCell::blank).count());
        GroupedTimelineEntry.ActivityCell stayHour = entry.activity().get(10 * 7);
        assertFalse(stayHour.blank());
        assertEquals(0, stayHour.level());
        GroupedTimelineEntry.ActivityCell tripHour = entry.activity().get(11 * 7);
        assertFalse(tripHour.blank());
        assertEquals(4, tripHour.level());
        assertTrue(entry.activity().stream().filter(c -> !c.blank()).allMatch(c -> c.title() != null));
    }

    @Test
    void testDistanceIsMappedIntoMovementView() {
        ZoneId tz = ZoneId.of("Europe/Berlin");
        LocalDate date = LocalDate.of(2025, 3, 3);
        Instant dayStart = date.atStartOfDay(tz).toInstant();

        ProcessedVisit visit1 = testingService.createVisit(user, place, dayStart.plus(8, ChronoUnit.HOURS), dayStart.plus(9, ChronoUnit.HOURS));
        ProcessedVisit visit2 = testingService.createVisit(user, place, dayStart.plus(10, ChronoUnit.HOURS), dayStart.plus(11, ChronoUnit.HOURS));

        Instant tripStart = visit1.getEndTime();
        Instant tripEnd = visit2.getStartTime();
        long duration = tripEnd.getEpochSecond() - tripStart.getEpochSecond();
        Long tripId = jdbcTemplate.queryForObject(
                "INSERT INTO trips (user_id, start_time, end_time, duration_seconds, estimated_distance_meters, travelled_distance_meters, start_visit_id, end_visit_id, metadata) VALUES (:userId, :start, :end, :duration, :distance, :distance, :startVisit, :endVisit, '{}'::jsonb) RETURNING id",
                Map.of(
                        "userId", user.getId(),
                        "start", Timestamp.from(tripStart),
                        "end", Timestamp.from(tripEnd),
                        "duration", duration,
                        "distance", 12000.0,
                        "startVisit", visit1.getId(),
                        "endVisit", visit2.getId()
                ),
                Long.class);
        jdbcTemplate.update("INSERT INTO trip_transport_modes (trip_id, offset_seconds, duration_in_seconds, transportation_mode, distance_meters) VALUES (:tripId, 0, :duration, :mode, :distance)",
                            Map.of(
                                    "tripId", tripId,
                                    "duration", duration,
                                    "mode", TransportMode.DRIVING.name(),
                                    "distance", 12000.0
                            ));

        List<GroupedTimelineEntry> entries = service.load(user, dayStart, date.atTime(LocalTime.MAX).atZone(tz).toInstant(), tz, LocalTime.MIDNIGHT, UnitSystem.METRIC);

        assertEquals(1, entries.size());
        GroupedTimelineEntry entry = entries.get(0);
        assertEquals("12", entry.formattedDistance());
        assertEquals("km", entry.distanceUnit());

        assertNotNull(entry.movement());
        assertEquals(2, entry.movement().bars().size());
        GroupedTimelineEntry.MovementView.Bar distanceBar = entry.movement().bars().get(0);
        assertEquals("distance", distanceBar.labelKey());
        assertEquals(1, distanceBar.segments().size());
        assertEquals("DRIVING", distanceBar.segments().get(0).mode());
        assertEquals(100.0, distanceBar.segments().get(0).percent(), 0.001);
        assertTrue(distanceBar.segments().get(0).title().contains("12 km"));
        assertEquals("time", entry.movement().bars().get(1).labelKey());

        GroupedTimelineEntry.TransportModePart part = entry.transportEntries().get(0).parts().get(0);
        assertEquals(12000L, part.distanceMeters());

        List<GroupedTimelineEntry> imperial = service.load(user, dayStart, date.atTime(LocalTime.MAX).atZone(tz).toInstant(), tz, LocalTime.MIDNIGHT, UnitSystem.IMPERIAL);
        assertEquals("7.5", imperial.get(0).formattedDistance());
        assertEquals("mi", imperial.get(0).distanceUnit());
    }

    @Test
    void testDayBoundaryShiftsVisitsToPreviousDay() {
        ZoneId tz = ZoneId.of("Europe/Berlin");
        LocalTime dayStart = LocalTime.of(4, 0);
        LocalDate rangeStart = LocalDate.of(2025, 1, 6);
        LocalDate rangeEnd = LocalDate.of(2025, 1, 19);
        LocalDate monday = LocalDate.of(2025, 1, 13);

        // Visit shortly after midnight on Monday belongs to Sunday with a 04:00 day boundary
        testingService.createVisit(user, place, monday.atTime(1, 0).atZone(tz).toInstant(), monday.atTime(2, 0).atZone(tz).toInstant());
        // Visit at noon belongs to Monday itself
        testingService.createVisit(user, place, monday.atTime(12, 0).atZone(tz).toInstant(), monday.atTime(13, 0).atZone(tz).toInstant());

        Instant start = com.dedicatedcode.reitti.service.TimeUtil.startOfDay(rangeStart, tz, dayStart);
        Instant end = com.dedicatedcode.reitti.service.TimeUtil.endOfDay(rangeEnd, tz, dayStart);

        List<GroupedTimelineEntry> entries = service.load(user, start, end, tz, dayStart, UnitSystem.METRIC);

        assertNotNull(entries);
        assertEquals(2, entries.size());

        GroupedTimelineEntry firstWeek = entries.get(0);
        GroupedTimelineEntry.OverviewEntry sunday = firstWeek.overview().stream()
                .filter(o -> o.slot().equals(LocalDate.of(2025, 1, 12)))
                .findFirst().orElseThrow();
        assertEquals(1, sunday.visits());
        assertEquals(0, sunday.trips());

        GroupedTimelineEntry secondWeek = entries.get(1);
        GroupedTimelineEntry.OverviewEntry mondayEntry = secondWeek.overview().stream()
                .filter(o -> o.slot().equals(monday))
                .findFirst().orElseThrow();
        assertEquals(1, mondayEntry.visits());
    }
}