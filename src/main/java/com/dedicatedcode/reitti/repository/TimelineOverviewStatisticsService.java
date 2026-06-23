package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.dto.timeline.GroupedTimelineEntry;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.metadata.Mood;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.ContextPathHolder;
import com.dedicatedcode.reitti.service.I18nService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
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

    public List<GroupedTimelineEntry> load(User user, Instant start, Instant end, ZoneId userTimezone) {

        Duration duration = Duration.between(start, end.plus(1, ChronoUnit.SECONDS));
        Granularity granularity = duration.toDays() >= 365 ? Granularity.MONTHLY : Granularity.WEEKLY;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("granularity", granularity.getSqlValue())
                .addValue("timezone", userTimezone.toString())
                .addValue("userId", user.getId())
                .addValue("start", Timestamp.from(start))
                .addValue("end", Timestamp.from(end));

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
                                                                                  DATE_TRUNC('day', t.start_time AT TIME ZONE :timezone)::date = d.day
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
                                                                                       DATE_TRUNC('day', pv.start_time AT TIME ZONE :timezone)::date = d.day
                                                                                       AND pv.user_id = :userId
                                                                                       AND pv.start_time >= :start AND pv.start_time <= :end
                                                                                   GROUP BY d.day
                                                                                   ORDER BY d.day;
                                                                                   """, params);

        List<Map<String, Object>> tripMoodCountsPerSlice = this.jdbcTemplate.queryForList("""
                                                                                         SELECT
                                                                                             DATE_TRUNC(:granularity, t.start_time AT TIME ZONE :timezone) AT TIME ZONE :timezone AS time_bucket,
                                                                                             t.transport_mode_inferred AS name,
                                                                                             lm.metadata->>'mood' AS mood,
                                                                                             SUM(t.duration_seconds)::BIGINT AS duration_seconds,
                                                                                             SUM(t.travelled_distance_meters)::BIGINT AS distance_meters,
                                                                                             COUNT(*) AS mood_count
                                                                                         FROM trips t
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
                                                                                              DATE_TRUNC(:granularity, v.start_time AT TIME ZONE :timezone) AT TIME ZONE :timezone AS time_bucket,
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
                                                                                          DATE_TRUNC(:granularity, v.start_time AT TIME ZONE :timezone) AT TIME ZONE :timezone AS time_bucket,
                                                                                          lm.metadata->>'mood' AS mood,
                                                                                          v.id,
                                                                                          s.id AS place_id,
                                                                                          s.name AS place_name,
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
                                                                                      GROUP BY 1, 2, 3, 4, 5
                                                                                      ORDER BY time_bucket;
                                                                                      """, params);

        List<Map<String, Object>> visits = this.jdbcTemplate.queryForList("""
                                                                          SELECT
                                                                              DATE_TRUNC(:granularity, pv.start_time AT TIME ZONE :timezone) AT TIME ZONE :timezone AS time_bucket,
                                                                              COALESCE(sp.name, 'Unknown / Unnamed Place') AS place_name, 
                                                                              COUNT(pv.id) AS amount
                                                                          FROM processed_visits pv
                                                                          LEFT JOIN significant_places sp ON pv.place_id = sp.id
                                                                          WHERE pv.user_id = :userId
                                                                            AND pv.start_time >= :start
                                                                            AND pv.start_time <= :end
                                                                          GROUP BY 1, 2
                                                                          ORDER BY 1, 3 DESC;
                                                                          """, params);

        Locale locale = LocaleContextHolder.getLocale();
        List<GroupedTimelineEntry> entries = new ArrayList<>();

        Map<LocalDate, List<Map<String, Object>>> splitTrips = splitupIntoDays(start, userTimezone, granularity, allTripsByLower);
        Map<LocalDate, List<Map<String, Object>>> splitVisits = splitupIntoDays(start, userTimezone, granularity, allVisitsByLower);

        Map<LocalDate, Long> amountOfTrips = calculateAmountPerSlice(granularity, userTimezone, allTripsByLower);
        Map<LocalDate, Long> amountOfPlaces = calculateAmountPerSlice(granularity, userTimezone, visits);

        Map<LocalDate, Map<TransportMode, List<GroupedTimelineEntry.TransportModePart>>> transportModeData = calculateTransportModeData(userTimezone, tripMoodCountsPerSlice);
        Map<LocalDate, Map<String, List<GroupedTimelineEntry.VisitPart>>> visitsMoodDurationData = calculateVisitsDataPerPlace(userTimezone, visitCountsPerSlice);

        Map<LocalDate, List<GroupedTimelineEntry.MoodValue>> visitMoods = calculateMoodRingValue(userTimezone, visitMoodCountsPerSlice);
        Map<LocalDate, List<GroupedTimelineEntry.MoodValue>> tripMoods = calculateMoodRingValue(userTimezone, tripMoodCountsPerSlice);

        Set<LocalDate> allExistingGroupKeys = extractKeys(splitTrips, splitVisits, transportModeData, visitMoods, tripMoods);

        List<LocalDate> sortedKeys = allExistingGroupKeys.stream().sorted().toList();

        for (LocalDate sortedKey : sortedKeys) {
            LocalDate endDate = granularity == Granularity.MONTHLY ? sortedKey.withDayOfMonth(sortedKey.lengthOfMonth()) : sortedKey.with(ChronoField.DAY_OF_WEEK, 1).plusWeeks(1);
            List<Map<String, Object>> tripsPerSegment = splitTrips.get(sortedKey);
            List<Map<String, Object>> visitsPerSegment = splitVisits.get(sortedKey);
            List<GroupedTimelineEntry.OverviewEntry> overviewEntries = new ArrayList<>();
            for (int i = 0; i < visitsPerSegment.size(); i++) {
                LocalDate day = visitsPerSegment.get(i).get("time_bucket") != null ? ((Timestamp) visitsPerSegment.get(i).get("time_bucket")).toInstant().atZone(userTimezone).toLocalDate() : null;
                long dailyAmountOfVisits = visitsPerSegment.get(i).get("amount") != null ? (long) visitsPerSegment.get(i).get("amount") : 0;
                long dailyAmountOfTrips = tripsPerSegment.get(i).get("amount") != null ? (long) tripsPerSegment.get(i).get("amount") : 0;
                overviewEntries.add(new GroupedTimelineEntry.OverviewEntry(day, dailyAmountOfVisits, dailyAmountOfTrips));
            }

            if (overviewEntries.stream().mapToLong(o -> o.visits() + o.trips()).sum() == 0) {
                log.debug("Skipping empty timeline entry for {}", sortedKey);
                continue;
            }

            Map<TransportMode, List<GroupedTimelineEntry.TransportModePart>> transportModeListMap = transportModeData.getOrDefault(sortedKey, Collections.emptyMap());
            List<GroupedTimelineEntry.TransportEntry> transportEntries = new ArrayList<>();
            transportModeListMap.forEach((transportMode, transportModeParts) -> transportEntries.add(new GroupedTimelineEntry.TransportEntry(transportMode, transportModeParts)));


            Map<String, List<GroupedTimelineEntry.VisitPart>> visitMoodDurationMap = visitsMoodDurationData.getOrDefault(sortedKey, Collections.emptyMap());
            List<GroupedTimelineEntry.VisitEntry> visitEntries = new ArrayList<>();
            visitMoodDurationMap.forEach((placeName, visitMoodDurationParts) -> visitEntries.add(new GroupedTimelineEntry.VisitEntry(placeName, visitMoodDurationParts)));

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
            subheadline = i18nService.translate("js.common.time-range", sortedKey.format(DateTimeFormatter.ofPattern("dd.MM").withLocale(locale)), rangeEnd.format(DateTimeFormatter.ofPattern("dd.MM.yyyy").withLocale(locale)));
            entries.add(new GroupedTimelineEntry(UUID.randomUUID(),
                                                 name,
                                                 subheadline,
                                                 String.format(contextPathHolder.getContextPath() + "/?startDate=%s&endDate=%s", sortedKey, endDate),
                                                 overviewEntries,
                                                 amountOfPlaces.get(sortedKey),
                                                 amountOfTrips.get(sortedKey),
                                                 visitMoods.get(sortedKey),
                                                 tripMoods.get(sortedKey),
                                                 transportEntries,
                                                 visitEntries));
        }
        return entries;
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
                    double percent = (double) moodDuration / (double) totalTripDuration;
                    return new GroupedTimelineEntry.TransportModePart(transportMode, t.get("mood") != null ? Mood.valueOf((String) t.get("mood")) : null, (long) t.get("duration_seconds"), percent);
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
                    String cleaned = StringUtils.hasText(placeName) ? placeName : null;
                    String moodValue = (String) partValue.get("mood");
                    Mood mood = moodValue != null ? Mood.valueOf(moodValue) : null;
                    parts.computeIfAbsent(cleaned, _ -> new ArrayList<>()).add(new GroupedTimelineEntry.VisitPart(placeId, cleaned, mood, duration, (double) duration / (double) totalDuration));
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
