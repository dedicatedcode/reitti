package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.dto.timeline.GroupedTimelineEntry;
import com.dedicatedcode.reitti.model.UnitSystem;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.metadata.Mood;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.ContextPathHolder;
import com.dedicatedcode.reitti.service.I18nService;
import com.dedicatedcode.reitti.service.statistics.StatisticsFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TimelineOverviewStatisticsService {

    private static final Logger log = LoggerFactory.getLogger(TimelineOverviewStatisticsService.class);
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final I18nService i18nService;
    private final ContextPathHolder contextPathHolder;

    public TimelineOverviewStatisticsService(NamedParameterJdbcTemplate jdbcTemplate, I18nService i18nService, ContextPathHolder contextPathHolder) {
        this.jdbcTemplate = jdbcTemplate;
        this.i18nService = i18nService;
        this.contextPathHolder = contextPathHolder;
    }

    public List<GroupedTimelineEntry> load(User user, Instant start, Instant end, ZoneId userTimezone, LocalTime dayStartTime, UnitSystem unitSystem) {

        Duration duration = Duration.between(start, end.plus(1, ChronoUnit.SECONDS));
        Granularity granularity = duration.toDays() >= 365 ? Granularity.MONTHLY : Granularity.WEEKLY;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("granularity", granularity.getSqlValue())
                .addValue("timezone", userTimezone.toString())
                .addValue("userId", user.getId())
                .addValue("start", Timestamp.from(start))
                .addValue("end", Timestamp.from(end))
                .addValue("dayStartMinutes", dayStartTime != null ? dayStartTime.get(ChronoField.MINUTE_OF_DAY) : 0);

        List<Map<String, Object>> allTripsByLower = this.jdbcTemplate.queryForList("""
                                                                                  SELECT
                                                                                      d.day::timestamp AS time_bucket, -- Explicitly forces a consistent Timestamp object type return
                                                                                      COUNT(t.id) AS amount
                                                                                  FROM (
                                                                                      SELECT GENERATE_SERIES(
                                                                                                 (:start AT TIME ZONE :timezone)::date,
                                                                                                 (:end AT TIME ZONE :timezone)::date,
                                                                                                 '1 day'::interval
                                                                                             )::date AS day
                                                                                  ) d LEFT JOIN trips t ON
                                                                                  ((t.start_time AT TIME ZONE :timezone) - make_interval(mins => :dayStartMinutes))::date = d.day
                                                                                      AND t.user_id = :userId
                                                                                      AND t.start_time >= :start AND t.start_time <= :end
                                                                                  GROUP BY d.day
                                                                                  ORDER BY d.day;
                                                                                  """, params);

        List<Map<String, Object>> allVisitsByLower = this.jdbcTemplate.queryForList("""
                                                                                   SELECT
                                                                                       d.day::timestamp AS time_bucket, -- Explicitly forces a consistent Timestamp object type return
                                                                                       COUNT(pv.id) AS amount
                                                                                   FROM (
                                                                                       SELECT GENERATE_SERIES(
                                                                                                  (:start AT TIME ZONE :timezone)::date,
                                                                                                  (:end AT TIME ZONE :timezone)::date,
                                                                                                  '1 day'::interval
                                                                                              )::date AS day
                                                                                   ) d
                                                                                   LEFT JOIN processed_visits pv ON
                                                                                       ((pv.start_time AT TIME ZONE :timezone) - make_interval(mins => :dayStartMinutes))::date = d.day
                                                                                       AND pv.user_id = :userId
                                                                                       AND pv.start_time >= :start AND pv.start_time <= :end
                                                                                   GROUP BY d.day
                                                                                   ORDER BY d.day;
                                                                                   """, params);

        List<Map<String, Object>> tripMoodCountsPerSlice = this.jdbcTemplate.queryForList("""
SELECT
                                                                                              DATE_TRUNC(:granularity, DATE_TRUNC('day', t.start_time AT TIME ZONE :timezone) - make_interval(mins => :dayStartMinutes)) AT TIME ZONE :timezone AS time_bucket,
                                                                                              tm.transportation_mode AS name,
                                                                                              lm.metadata->>'mood' AS mood,
                                                                                              SUM(tm.duration_in_seconds)::BIGINT AS duration_seconds,
                                                                                              SUM(tm.distance_meters)::BIGINT AS distance_meters,
                                                                                              COUNT(*) AS mood_count
                                                                                          FROM trips t
                                                                                          INNER JOIN trip_transport_modes tm ON tm.trip_id = t.id
                                                                                          LEFT JOIN LATERAL (
                                                                                              SELECT metadata
                                                                                              FROM location_metadata lm
                                                                                              WHERE lm.user_id = :userId
                                                                                                AND lm.time_range && TSTZRANGE(t.start_time, t.end_time)
                                                                                                AND lm.context_type = 'TRIP'
                                                                                                AND lm.metadata->>'mood' IS NOT NULL
                                                                                              ORDER BY UPPER(lm.time_range * TSTZRANGE(t.start_time, t.end_time))
                                                                                                     - LOWER(lm.time_range * TSTZRANGE(t.start_time, t.end_time)) DESC
                                                                                              LIMIT 1
                                                                                          ) lm ON TRUE
                                                                                          WHERE t.user_id = :userId
                                                                                            AND t.start_time >= :start AND t.start_time <= :end
                                                                                            AND t.start_time < t.end_time
                                                                                          GROUP BY 1, 2, 3
                                                                                          ORDER BY time_bucket;
                                                                                          """, params);

        List<Map<String, Object>> visitMoodCountsPerSlice = this.jdbcTemplate.queryForList("""
SELECT
                                                                                           DATE_TRUNC(:granularity, DATE_TRUNC('day', v.start_time AT TIME ZONE :timezone) - make_interval(mins => :dayStartMinutes)) AT TIME ZONE :timezone AS time_bucket,
                                                                                           lm.metadata->>'mood' AS mood,
                                                                                           SUM(v.duration_seconds)::BIGINT AS duration_seconds,
                                                                                           COUNT(*) AS mood_count
                                                                                       FROM processed_visits v
                                                                                       LEFT JOIN LATERAL (
                                                                                              SELECT metadata
                                                                                              FROM location_metadata lm
                                                                                              WHERE lm.user_id = :userId
                                                                                                AND lm.time_range && TSTZRANGE(v.start_time, v.end_time)
                                                                                                AND lm.context_type = 'VISIT'
                                                                                                AND lm.metadata->>'mood' IS NOT NULL
                                                                                              ORDER BY UPPER(lm.time_range * TSTZRANGE(v.start_time, v.end_time))
                                                                                                     - LOWER(lm.time_range * TSTZRANGE(v.start_time, v.end_time)) DESC
                                                                                              LIMIT 1
                                                                                          ) lm ON TRUE
                                                                                          WHERE v.user_id = :userId
                                                                                            AND v.start_time >= :start AND v.start_time <= :end
                                                                                            AND v.start_time < v.end_time
                                                                                          GROUP BY 1, 2
                                                                                          ORDER BY time_bucket;
                                                                                          """, params);


        List<Map<String, Object>> visitCountsPerSlice = this.jdbcTemplate.queryForList("""
SELECT
                                                                                           DATE_TRUNC(:granularity, DATE_TRUNC('day', v.start_time AT TIME ZONE :timezone) - make_interval(mins => :dayStartMinutes)) AT TIME ZONE :timezone AS time_bucket,
                                                                                           lm.metadata->>'mood' AS mood,
                                                                                           v.id,
                                                                                           s.id AS place_id,
                                                                                           s.name AS place_name,
                                                                                           s.latitude_centroid,
                                                                                           s.longitude_centroid,
                                                                                           SUM(v.duration_seconds)::BIGINT AS duration_seconds,
                                                                                           COUNT(*) AS mood_count
                                                                                      FROM processed_visits v
                                                                                      LEFT JOIN LATERAL (
                                                                                          SELECT metadata
                                                                                          FROM location_metadata lm
                                                                                          WHERE lm.user_id = :userId
                                                                                            AND lm.time_range && TSTZRANGE(v.start_time, v.end_time)
                                                                                            AND lm.context_type = 'VISIT'
                                                                                            AND lm.metadata->>'mood' IS NOT NULL
                                                                                          ORDER BY UPPER(lm.time_range * TSTZRANGE(v.start_time, v.end_time))
                                                                                                 - LOWER(lm.time_range * TSTZRANGE(v.start_time, v.end_time)) DESC
                                                                                          LIMIT 1
                                                                                      ) lm ON TRUE
                                                                                      LEFT JOIN significant_places s ON s.id = v.place_id
                                                                                      WHERE v.user_id = :userId
                                                                                        AND v.start_time >= :start AND v.start_time <= :end
                                                                                      GROUP BY 1, 2, 3, 4, 5, 6, 7
                                                                                      ORDER BY time_bucket;
                                                                                      """, params);

        List<Map<String, Object>> visits = this.jdbcTemplate.queryForList("""
                                                                            SELECT
                                                                                DATE_TRUNC(:granularity, DATE_TRUNC('day', pv.start_time AT TIME ZONE :timezone) - make_interval(mins => :dayStartMinutes)) AT TIME ZONE :timezone AS time_bucket,
                                                                                COUNT(DISTINCT pv.place_id) AS amount
                                                                            FROM processed_visits pv
                                                                            WHERE pv.user_id = :userId
                                                                              AND pv.start_time >= :start
                                                                              AND pv.start_time <= :end
                                                                            GROUP BY 1
                                                                            ORDER BY 1;
                                                                            """, params);

        List<Map<String, Object>> hourlyActivity = granularity == Granularity.WEEKLY ? this.jdbcTemplate.queryForList("""
                                                                                       SELECT h.slot_start AS time_bucket,
                                                                                              COALESCE(t.seconds, 0)::BIGINT AS activity_seconds
                                                                                       FROM (
                                                                                           SELECT GENERATE_SERIES(
                                                                                               (:start AT TIME ZONE :timezone),
                                                                                               (:end AT TIME ZONE :timezone),
                                                                                               '1 hour'::interval
                                                                                           ) AS slot_start
                                                                                       ) h
                                                                                       LEFT JOIN LATERAL (
                                                                                           SELECT SUM(EXTRACT(EPOCH FROM (
                                                                                               LEAST(t.end_time AT TIME ZONE :timezone, h.slot_start + interval '1 hour')
                                                                                               - GREATEST(t.start_time AT TIME ZONE :timezone, h.slot_start)
                                                                                           ))) AS seconds
                                                                                           FROM trips t
                                                                                           WHERE t.user_id = :userId
                                                                                             AND t.start_time AT TIME ZONE :timezone < h.slot_start + interval '1 hour'
                                                                                             AND t.end_time AT TIME ZONE :timezone > h.slot_start
                                                                                       ) t ON TRUE
                                                                                       ORDER BY h.slot_start;
                                                                                       """, params) : Collections.emptyList();

        Locale locale = LocaleContextHolder.getLocale();
        StatisticsFormatter formatter = new StatisticsFormatter(i18nService, locale, unitSystem, null);
        List<GroupedTimelineEntry> entries = new ArrayList<>();

        Map<LocalDate, List<Map<String, Object>>> splitTrips = splitupIntoDays(start, userTimezone, granularity, allTripsByLower);
        Map<LocalDate, List<Map<String, Object>>> splitVisits = splitupIntoDays(start, userTimezone, granularity, allVisitsByLower);

        Map<LocalDate, Long> amountOfTrips = calculateAmountPerSlice(granularity, userTimezone, allTripsByLower);
        Map<LocalDate, Long> amountOfPlaces = calculateAmountPerSlice(granularity, userTimezone, visits);

        Map<LocalDate, Map<TransportMode, List<GroupedTimelineEntry.TransportModePart>>> transportModeData = calculateTransportModeData(userTimezone, tripMoodCountsPerSlice);
        Map<LocalDate, Map<String, List<GroupedTimelineEntry.VisitPart>>> visitsMoodDurationData = calculateVisitsDataPerPlace(userTimezone, visitCountsPerSlice);

        Map<LocalDate, List<GroupedTimelineEntry.MoodValue>> visitMoods = calculateMoodRingValue(userTimezone, visitMoodCountsPerSlice);
        Map<LocalDate, List<GroupedTimelineEntry.MoodValue>> tripMoods = calculateMoodRingValue(userTimezone, tripMoodCountsPerSlice);

        Map<LocalDate, long[]> activityPerHour = calculateHourlyActivity(hourlyActivity);

        Set<LocalDate> allExistingGroupKeys = extractKeys(splitTrips, splitVisits, transportModeData, visitMoods, tripMoods);

        List<LocalDate> sortedKeys = allExistingGroupKeys.stream().sorted().toList();

        for (LocalDate sortedKey : sortedKeys) {
            List<Map<String, Object>> tripsPerSegment = splitTrips.get(sortedKey);
            List<Map<String, Object>> visitsPerSegment = splitVisits.get(sortedKey);
            List<GroupedTimelineEntry.OverviewEntry> overviewEntries = new ArrayList<>();
            for (int i = 0; i < visitsPerSegment.size(); i++) {
                LocalDate day = visitsPerSegment.get(i).get("time_bucket") != null ? ((Timestamp) visitsPerSegment.get(i).get("time_bucket")).toLocalDateTime().toLocalDate() : null;
                long dailyAmountOfVisits = visitsPerSegment.get(i).get("amount") != null ? (long) visitsPerSegment.get(i).get("amount") : 0;
                long dailyAmountOfTrips = tripsPerSegment.get(i).get("amount") != null ? (long) tripsPerSegment.get(i).get("amount") : 0;
                overviewEntries.add(new GroupedTimelineEntry.OverviewEntry(day, dailyAmountOfVisits, dailyAmountOfTrips, 0, null));
            }

            if (overviewEntries.stream().mapToLong(o -> o.visits() + o.trips()).sum() == 0) {
                log.debug("Skipping empty timeline entry for {}", sortedKey);
                continue;
            }

            overviewEntries = withActivityLevels(overviewEntries, formatter);
            int leadingBlanks = overviewEntries.getFirst().slot() != null ? overviewEntries.getFirst().slot().getDayOfWeek().getValue() - 1 : 0;

            Map<TransportMode, List<GroupedTimelineEntry.TransportModePart>> transportModeListMap = transportModeData.getOrDefault(sortedKey, Collections.emptyMap());
            List<GroupedTimelineEntry.TransportEntry> transportEntries = new ArrayList<>();
            transportModeListMap.forEach((transportMode, transportModeParts) -> transportEntries.add(new GroupedTimelineEntry.TransportEntry(transportMode, transportModeParts)));


            Map<String, List<GroupedTimelineEntry.VisitPart>> visitMoodDurationMap = visitsMoodDurationData.getOrDefault(sortedKey, Collections.emptyMap());
            List<GroupedTimelineEntry.VisitEntry> visitEntries = new ArrayList<>();
            visitMoodDurationMap.forEach((placeName, visitMoodDurationParts) -> visitEntries.add(new GroupedTimelineEntry.VisitEntry(placeName, visitMoodDurationParts)));

            long totalDistanceMeters = transportEntries.stream().mapToLong(GroupedTimelineEntry.TransportEntry::totalDistanceMeters).sum();

            String name;
            String subheadline;
            if (granularity == Granularity.MONTHLY) {
                name = sortedKey.format(DateTimeFormatter.ofPattern("MMMM yyyy").withLocale(locale));
            } else {
                name = i18nService.translate("timeline.grouped.headline.weekly", sortedKey.get(WeekFields.of(locale).weekOfYear()));
            }
            LocalDate rangeEnd = granularity == Granularity.MONTHLY ?
                    sortedKey.withDayOfMonth(1).plusMonths(1).minusDays(1) :
                    sortedKey.with(ChronoField.DAY_OF_WEEK, 1).plusWeeks(1).minusDays(1);
            LocalDate adjustedRangeStart = sortedKey.isBefore(start.atZone(userTimezone).toLocalDate()) ? start.atZone(userTimezone).toLocalDate() : sortedKey;
            LocalDate adjustedRangeEnd = rangeEnd.isAfter(end.atZone(userTimezone).toLocalDate()) ? end.atZone(userTimezone).toLocalDate() : rangeEnd;
            subheadline = i18nService.translate("js.common.time-range", adjustedRangeStart.format(DateTimeFormatter.ofPattern("dd.MM").withLocale(locale)), adjustedRangeEnd.format(DateTimeFormatter.ofPattern("dd.MM.yyyy").withLocale(locale)));
            List<GroupedTimelineEntry.ActivityCell> activity = granularity == Granularity.WEEKLY ?
                    buildActivityCells(sortedKey, adjustedRangeStart, adjustedRangeEnd, activityPerHour, formatter, locale) :
                    Collections.emptyList();
            entries.add(new GroupedTimelineEntry(UUID.randomUUID(),
                                                 name,
                                                 subheadline,
                                                 String.format(contextPathHolder.getContextPath() + "/?startDate=%s&endDate=%s", adjustedRangeStart, adjustedRangeEnd),
                                                 granularity == Granularity.MONTHLY,
                                                 leadingBlanks,
                                                 overviewEntries,
                                                 activity,
                                                 formatter.distanceNumber(totalDistanceMeters),
                                                 formatter.distanceUnit(),
                                                 amountOfPlaces.get(sortedKey),
                                                 amountOfTrips.get(sortedKey),
                                                 visitMoods.get(sortedKey),
                                                 tripMoods.get(sortedKey),
                                                 transportEntries,
                                                 visitEntries,
                                                 buildMovementView(transportEntries, formatter),
                                                 buildTopPlaces(visitEntries, formatter)));
        }
        return entries;
    }

    private List<GroupedTimelineEntry.OverviewEntry> withActivityLevels(List<GroupedTimelineEntry.OverviewEntry> entries, StatisticsFormatter formatter) {
        long maxActivity = entries.stream().mapToLong(e -> e.visits() + e.trips()).max().orElse(0);
        return entries.stream().map(e -> {
            long activity = e.visits() + e.trips();
            int level = activity == 0 ? 0 : Math.max(1, (int) Math.ceil(4.0 * activity / maxActivity));
            String title = i18nService.translate("timeline.grouped.activity.tooltip", e.slot() != null ? formatter.day(e.slot()) : "", e.visits(), e.trips());
            return new GroupedTimelineEntry.OverviewEntry(e.slot(), e.visits(), e.trips(), level, title);
        }).toList();
    }

    private Map<LocalDate, long[]> calculateHourlyActivity(List<Map<String, Object>> hourlyActivity) {
        Map<LocalDate, long[]> result = new HashMap<>();
        for (Map<String, Object> row : hourlyActivity) {
            LocalDateTime slotStart = ((Timestamp) row.get("time_bucket")).toLocalDateTime();
            long seconds = row.get("activity_seconds") != null ? ((Number) row.get("activity_seconds")).longValue() : 0L;
            result.computeIfAbsent(slotStart.toLocalDate(), _ -> new long[24])[slotStart.getHour()] = seconds;
        }
        return result;
    }

    private List<GroupedTimelineEntry.ActivityCell> buildActivityCells(LocalDate weekStart, LocalDate rangeStart, LocalDate rangeEnd, Map<LocalDate, long[]> activityPerHour, StatisticsFormatter formatter, Locale locale) {
        List<GroupedTimelineEntry.ActivityCell> cells = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            for (int dow = 0; dow < 7; dow++) {
                LocalDate date = weekStart.plusDays(dow);
                if (date.isBefore(rangeStart) || date.isAfter(rangeEnd)) {
                    cells.add(new GroupedTimelineEntry.ActivityCell(true, 0, null, null));
                    continue;
                }
                long[] hours = activityPerHour.get(date);
                long seconds = hours != null ? hours[hour] : 0;
                int level = seconds == 0 ? 0 : Math.clamp((int) Math.ceil(4.0 * seconds / 3600.0), 1, 4);
                String title = i18nService.translate("timeline.grouped.activity.hourly.tooltip",
                                                     date.getDayOfWeek().getDisplayName(TextStyle.SHORT, locale),
                                                     String.format("%02d:00", hour),
                                                     String.format("%02d:00", hour + 1),
                                                     formatter.duration(seconds));
                cells.add(new GroupedTimelineEntry.ActivityCell(false, level, title, date));
            }
        }
        return cells;
    }

    private GroupedTimelineEntry.MovementView buildMovementView(List<GroupedTimelineEntry.TransportEntry> transportEntries, StatisticsFormatter formatter) {
        List<GroupedTimelineEntry.TransportEntry> sorted = transportEntries.stream()
                .sorted(Comparator.comparingLong(GroupedTimelineEntry.TransportEntry::durationSeconds).reversed())
                .toList();
        long totalDurationSeconds = sorted.stream().mapToLong(GroupedTimelineEntry.TransportEntry::durationSeconds).sum();
        long totalDistanceMeters = sorted.stream().mapToLong(GroupedTimelineEntry.TransportEntry::totalDistanceMeters).sum();
        if (totalDurationSeconds == 0) {
            return null;
        }

        List<GroupedTimelineEntry.MovementView.Segment> distanceSegments = new ArrayList<>();
        List<GroupedTimelineEntry.MovementView.Segment> timeSegments = new ArrayList<>();
        List<GroupedTimelineEntry.MovementView.LegendEntry> legend = new ArrayList<>();
        for (GroupedTimelineEntry.TransportEntry transportEntry : sorted) {
            String mode = transportEntry.transportMode().name();
            String modeName = formatter.modeName(transportEntry.transportMode());
            long durationSeconds = transportEntry.durationSeconds();
            long distanceMeters = transportEntry.totalDistanceMeters();

            if (totalDistanceMeters > 0 && distanceMeters > 0) {
                double share = (double) distanceMeters / totalDistanceMeters;
                distanceSegments.add(new GroupedTimelineEntry.MovementView.Segment(mode, share * 100, modeName + " · " + formatter.distance(distanceMeters) + " · " + formatter.percent(share)));
            }
            double timeShare = (double) durationSeconds / totalDurationSeconds;
            timeSegments.add(new GroupedTimelineEntry.MovementView.Segment(mode, timeShare * 100, modeName + " · " + formatter.duration(durationSeconds) + " · " + formatter.percent(timeShare)));

            String moodIcon = transportEntry.dominantMood().map(Mood::getIcon).orElse(null);
            legend.add(new GroupedTimelineEntry.MovementView.LegendEntry(mode, modeName, moodIcon));
        }

        List<GroupedTimelineEntry.MovementView.Bar> bars = new ArrayList<>();
        if (!distanceSegments.isEmpty()) {
            bars.add(new GroupedTimelineEntry.MovementView.Bar("distance", distanceSegments));
        }
        bars.add(new GroupedTimelineEntry.MovementView.Bar("time", timeSegments));
        return new GroupedTimelineEntry.MovementView(bars, legend);
    }

    private List<GroupedTimelineEntry.TopPlace> buildTopPlaces(List<GroupedTimelineEntry.VisitEntry> visitEntries, StatisticsFormatter formatter) {
        return visitEntries.stream()
                .filter(v -> v.name() != null && !v.parts().isEmpty())
                .sorted(Comparator.comparingLong(GroupedTimelineEntry.VisitEntry::durationSeconds).reversed())
                .limit(5)
                .map(v -> {
                    GroupedTimelineEntry.VisitPart part = v.parts().getFirst();
                    return new GroupedTimelineEntry.TopPlace(v.name(), formatter.duration(v.durationSeconds()), part.latitude(), part.longitude());
                })
                .toList();
    }

    private Double toDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private Set<LocalDate> extractKeys(Map<LocalDate, List<Map<String, Object>>> amountOfTrips, Map<LocalDate, List<Map<String, Object>>> splitVisits, Map<LocalDate, Map<TransportMode, List<GroupedTimelineEntry.TransportModePart>>> transportModeData, Map<LocalDate, List<GroupedTimelineEntry.MoodValue>> visitMoods, Map<LocalDate, List<GroupedTimelineEntry.MoodValue>> tripMoods) {
        Set<LocalDate> allExistingGroupKeys = new HashSet<>();
        allExistingGroupKeys.addAll(amountOfTrips.keySet());
        allExistingGroupKeys.addAll(splitVisits.keySet());
        allExistingGroupKeys.addAll(transportModeData.keySet());
        allExistingGroupKeys.addAll(visitMoods.keySet());
        allExistingGroupKeys.addAll(tripMoods.keySet());

        return allExistingGroupKeys;
    }

    private Map<LocalDate, List<GroupedTimelineEntry.MoodValue>> calculateMoodRingValue(ZoneId userTimezone, List<Map<String, Object>> visitMoodCountsPerSlice) {
        Map<LocalDate, List<GroupedTimelineEntry.MoodValue>> result = new HashMap<>();
        Map<LocalDate, List<Map<String, Object>>> byGranularity = visitMoodCountsPerSlice.stream().collect(Collectors.groupingBy(t -> ((Timestamp) t.get("time_bucket")).toInstant().atZone(userTimezone).toLocalDate()));
        for (LocalDate localDate : byGranularity.keySet()) {
            List<GroupedTimelineEntry.MoodValue> moodRing = new ArrayList<>();
            List<Map<String, Object>> byDate = byGranularity.get(localDate);
            for (Map<String, Object> stringObjectMap : byDate) {
                long amount = (Long) stringObjectMap.get("mood_count");
                long duration = (Long) stringObjectMap.get("duration_seconds");
                Mood mood = stringObjectMap.get("mood") != null ? Mood.valueOf((String) stringObjectMap.get("mood")) : null;
                moodRing.add(new GroupedTimelineEntry.MoodValue(mood, amount, duration));
            }
            result.put(localDate, moodRing);
        }

        return result;

    }

    private Map<LocalDate, Map<TransportMode, List<GroupedTimelineEntry.TransportModePart>>> calculateTransportModeData(ZoneId userTimezone, List<Map<String, Object>> tripMoodCountsPerSlice) {
        Map<LocalDate, Map<TransportMode, List<GroupedTimelineEntry.TransportModePart>>> result = new HashMap<>();
        Map<LocalDate, List<Map<String, Object>>> byGranularity = tripMoodCountsPerSlice.stream().collect(Collectors.groupingBy(t -> ((Timestamp) t.get("time_bucket")).toInstant().atZone(userTimezone).toLocalDate()));

        for (LocalDate localDate : byGranularity.keySet()) {
            Map<TransportMode, List<GroupedTimelineEntry.TransportModePart>> transportModesByDate = new HashMap<>();
            Map<TransportMode, List<Map<String, Object>>> byTransportMode = byGranularity.get(localDate).stream().collect(Collectors.groupingBy(t -> TransportMode.valueOf((String) t.get("name"))));
            for (TransportMode transportMode : byTransportMode.keySet()) {
                List<Map<String, Object>> transportModeData = byTransportMode.get(transportMode);
                long totalTripDuration = transportModeData.stream().mapToLong(t -> (Long) t.get("duration_seconds")).sum();
                List<GroupedTimelineEntry.TransportModePart> transportModeParts = transportModeData.stream().map(t -> {
                    long moodDuration = (long) t.get("duration_seconds");
                    long distanceMeters = t.get("distance_meters") != null ? (long) t.get("distance_meters") : 0L;
                    double percent = (double) moodDuration / (double) totalTripDuration;
                    return new GroupedTimelineEntry.TransportModePart(transportMode, t.get("mood") != null ? Mood.valueOf((String) t.get("mood")) : null, (long) t.get("duration_seconds"), distanceMeters, percent);
                }).sorted((o1, o2) -> (int) (o2.percent() - o1.percent())).toList();
                transportModesByDate.put(transportMode, transportModeParts);
            }
            result.put(localDate, transportModesByDate);
        }
        return result;
    }

    private Map<LocalDate, Map<String, List<GroupedTimelineEntry.VisitPart>>> calculateVisitsDataPerPlace(ZoneId userTimezone, List<Map<String, Object>> visitMoodsPerSlice) {
        Map<LocalDate, Map<String, List<GroupedTimelineEntry.VisitPart>>> result = new HashMap<>();
        Map<LocalDate, List<Map<String, Object>>> byGranularity = visitMoodsPerSlice.stream().collect(Collectors.groupingBy(t -> ((Timestamp) t.get("time_bucket")).toInstant().atZone(userTimezone).toLocalDate()));

        for (LocalDate localDate : byGranularity.keySet()) {
            Map<String, List<GroupedTimelineEntry.VisitPart>> parts = new HashMap<>();
            Map<String, List<Map<String, Object>>> byDateAndName = byGranularity.get(localDate).stream().collect(Collectors.groupingBy(t -> t.get("place_name") != null ? (String) t.get("place_name") : ""));

            for (String placeName : byDateAndName.keySet()) {
                List<Map<String, Object>> partValues = byDateAndName.get(placeName);
                long totalDuration = partValues.stream().mapToLong(t -> (Long) t.get("duration_seconds")).sum();
                for (Map<String, Object> partValue : partValues) {
                    long duration = (long) partValue.get("duration_seconds");
                    Long placeId = (Long) partValue.get("place_id");
                    Double latitude = toDouble(partValue.get("latitude_centroid"));
                    Double longitude = toDouble(partValue.get("longitude_centroid"));
                    String cleaned = StringUtils.hasText(placeName) ? placeName : null;
                    String moodValue = (String) partValue.get("mood");
                    Mood mood = moodValue != null ? Mood.valueOf(moodValue) : null;
                    parts.computeIfAbsent(cleaned, _ -> new ArrayList<>()).add(new GroupedTimelineEntry.VisitPart(placeId, cleaned, mood, duration, (double) duration / (double) totalDuration, latitude, longitude));
                }
            }
            if (!parts.isEmpty()) {
                result.put(localDate, parts);
            }
        }
        return result;
    }

    private Map<LocalDate, Long> calculateAmountPerSlice(Granularity granularity, ZoneId userTimezone, List<Map<String, Object>> entries) {
        TemporalAdjuster temporalAdjuster = granularity == Granularity.WEEKLY ?
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY) :
                TemporalAdjusters.firstDayOfMonth();
        Map<LocalDate, List<Map<String, Object>>> timeBucket = entries.stream()
                .collect(Collectors.groupingBy(
                        t -> ((Timestamp) t.get("time_bucket")).toInstant().atZone(userTimezone).toLocalDate()
                                .with(temporalAdjuster)
                ));
        Map<LocalDate, Long> timeBucketMap = new HashMap<>();
        for (LocalDate localDate : timeBucket.keySet()) {
            timeBucketMap.put(localDate, timeBucket.get(localDate).stream().mapToLong(t -> (Long) t.get("amount")).sum());
        }
        return timeBucketMap;
    }

    private Map<LocalDate, List<Map<String, Object>>> splitupIntoDays(Instant start, ZoneId userTimezone, Granularity safeGranularity, List<Map<String, Object>> allTripsByLower) {
        LocalDate current = start.atZone(userTimezone).toLocalDate();
        LocalDate currentSlotStart = safeGranularity == Granularity.MONTHLY ? current.withDayOfMonth(1) : current.with(ChronoField.DAY_OF_WEEK, 1);
        LocalDate currentSlotEnd = safeGranularity == Granularity.MONTHLY ? current.withDayOfMonth(1).plusMonths(1) : current.with(ChronoField.DAY_OF_WEEK, 1).plusWeeks(1);
        Map<LocalDate, List<Map<String, Object>>> splitTrips = new HashMap<>();
        splitTrips.put(currentSlotStart, new ArrayList<>());

        for (Map<String, Object> stringObjectMap : allTripsByLower) {
            Timestamp timeBucket = (Timestamp) stringObjectMap.get("time_bucket");
            LocalDate currentTimeDay = timeBucket.toLocalDateTime().toLocalDate();
            if (!currentTimeDay.isBefore(currentSlotEnd)) {
                currentSlotStart = currentSlotEnd;
                currentSlotEnd = safeGranularity == Granularity.MONTHLY ? currentSlotEnd.plusMonths(1) : currentSlotEnd.plusWeeks(1);
            }
            splitTrips.computeIfAbsent(currentSlotStart, _ -> new ArrayList<>()).add(stringObjectMap);

        }
        return splitTrips;
    }

    private enum Granularity {
        WEEKLY("week"),
        MONTHLY("month");

        private final String sqlValue;

        Granularity(String sqlValue) {
            this.sqlValue = sqlValue;
        }

        public String getSqlValue() {
            return sqlValue;
        }
    }
}
