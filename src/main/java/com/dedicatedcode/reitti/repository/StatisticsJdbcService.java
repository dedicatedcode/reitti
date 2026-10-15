package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.statistics.StatisticsData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoField;
import java.util.*;

@Service
public class StatisticsJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public StatisticsJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<Long, StatisticsData.Place> findPlaces(User user) {
        Map<Long, StatisticsData.Place> result = new HashMap<>();
        jdbcTemplate.query("""
                           SELECT id, name, city, country_code, type, latitude_centroid, longitude_centroid, timezone
                           FROM significant_places WHERE user_id = ?
                           """, rs -> {
            String timezone = rs.getString("timezone");
            String type = rs.getString("type");
            StatisticsData.Place place = new StatisticsData.Place(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("city"),
                    rs.getString("country_code"),
                    type != null ? SignificantPlace.PlaceType.valueOf(type) : SignificantPlace.PlaceType.OTHER,
                    rs.getDouble("latitude_centroid"),
                    rs.getDouble("longitude_centroid"),
                    timezone != null ? ZoneId.of(timezone) : null);
            result.put(place.id(), place);
        }, user.getId());
        return result;
    }

    public List<StatisticsData.Visit> findVisits(User user, Instant start, Instant end) {
        return jdbcTemplate.query("""
                                  SELECT place_id, start_time, end_time FROM processed_visits
                                  WHERE user_id = ? AND start_time < ? AND end_time > ?
                                  ORDER BY start_time
                                  """,
                                  (rs, _) -> new StatisticsData.Visit(
                                          rs.getLong("place_id"),
                                          rs.getTimestamp("start_time").toInstant(),
                                          rs.getTimestamp("end_time").toInstant()),
                                  user.getId(), Timestamp.from(end), Timestamp.from(start));
    }

    public List<StatisticsData.Trip> findTrips(User user, Instant start, Instant end) {
        Map<Long, List<StatisticsData.Segment>> segments = new HashMap<>();
        jdbcTemplate.query("""
                           SELECT tm.trip_id, tm.offset_seconds, tm.duration_in_seconds, tm.transportation_mode, tm.distance_meters
                           FROM trip_transport_modes tm JOIN trips t ON t.id = tm.trip_id
                           WHERE t.user_id = ? AND t.start_time < ? AND t.end_time > ?
                           ORDER BY tm.trip_id, tm.offset_seconds
                           """, rs -> {
            String mode = rs.getString("transportation_mode");
            segments.computeIfAbsent(rs.getLong("trip_id"), _ -> new ArrayList<>())
                    .add(new StatisticsData.Segment(
                            mode != null ? TransportMode.valueOf(mode) : TransportMode.UNKNOWN,
                            rs.getLong("offset_seconds"),
                            rs.getLong("duration_in_seconds"),
                            rs.getDouble("distance_meters")));
        }, user.getId(), Timestamp.from(end), Timestamp.from(start));

        return jdbcTemplate.query("""
                                  SELECT t.id, t.start_time, t.end_time, t.travelled_distance_meters, t.estimated_distance_meters,
                                         sv.place_id AS start_place_id, ev.place_id AS end_place_id
                                  FROM trips t
                                  LEFT JOIN processed_visits sv ON sv.id = t.start_visit_id
                                  LEFT JOIN processed_visits ev ON ev.id = t.end_visit_id
                                  WHERE t.user_id = ? AND t.start_time < ? AND t.end_time > ?
                                  ORDER BY t.start_time
                                  """, (rs, _) -> {
            long id = rs.getLong("id");
            List<StatisticsData.Segment> tripSegments = segments.getOrDefault(id, List.of());
            double travelled = rs.getDouble("travelled_distance_meters");
            double estimated = rs.getDouble("estimated_distance_meters");
            double segmentDistance = tripSegments.stream().mapToDouble(StatisticsData.Segment::distanceMeters).sum();
            double distance = travelled > 0 ? travelled : (segmentDistance > 0 ? segmentDistance : estimated);
            long startPlaceId = rs.getLong("start_place_id");
            Long startPlace = rs.wasNull() ? null : startPlaceId;
            long endPlaceId = rs.getLong("end_place_id");
            Long endPlace = rs.wasNull() ? null : endPlaceId;
            return new StatisticsData.Trip(id,
                                           rs.getTimestamp("start_time").toInstant(),
                                           rs.getTimestamp("end_time").toInstant(),
                                           startPlace,
                                           endPlace,
                                           distance,
                                           tripSegments);
        }, user.getId(), Timestamp.from(end), Timestamp.from(start));
    }

    public Map<Long, Instant> findFirstVisitPerPlace(User user) {
        Map<Long, Instant> result = new HashMap<>();
        jdbcTemplate.query("SELECT place_id, MIN(start_time) AS first_visit FROM processed_visits WHERE user_id = ? GROUP BY place_id",
                           rs -> {
                               result.put(rs.getLong("place_id"), rs.getTimestamp("first_visit").toInstant());
                           }, user.getId());
        return result;
    }

    public Optional<Instant[]> findDataRange(User user) {
        return jdbcTemplate.query("SELECT MIN(start_time) AS first_visit, MAX(end_time) AS last_visit FROM processed_visits WHERE user_id = ?",
                                  rs -> {
                                      if (!rs.next() || rs.getTimestamp("first_visit") == null) {
                                          return Optional.empty();
                                      }
                                      return Optional.of(new Instant[]{rs.getTimestamp("first_visit").toInstant(), rs.getTimestamp("last_visit").toInstant()});
                                  }, user.getId());
    }

    public List<YearMonth> findAvailableMonths(User user, ZoneId zoneId, LocalTime dayStartTime) {
        int dayStartMinutes = dayStartTime != null ? dayStartTime.get(ChronoField.MINUTE_OF_DAY) : 0;
        return jdbcTemplate.query("""
                                  SELECT DISTINCT EXTRACT(YEAR FROM local_start)::int AS y, EXTRACT(MONTH FROM local_start)::int AS m
                                  FROM (SELECT (start_time AT TIME ZONE ?) - make_interval(mins => ?) AS local_start
                                        FROM processed_visits WHERE user_id = ?) v
                                  ORDER BY y, m
                                  """,
                                  (rs, _) -> YearMonth.of(rs.getInt("y"), rs.getInt("m")),
                                  zoneId.getId(), dayStartMinutes, user.getId());
    }

    public List<StatisticsData.Memory> findMemories(User user, Instant start, Instant end) {
        return jdbcTemplate.query("""
                                  SELECT start_date, COALESCE(end_date, start_date + INTERVAL '1 day') AS end_date FROM memory
                                  WHERE user_id = ? AND start_date < ? AND COALESCE(end_date, start_date + INTERVAL '1 day') > ?
                                  """,
                                  (rs, _) -> new StatisticsData.Memory(rs.getTimestamp("start_date").toInstant(), rs.getTimestamp("end_date").toInstant()),
                                  user.getId(), Timestamp.from(end), Timestamp.from(start));
    }

    public Set<Long> findDismissedHomeSuggestions(User user) {
        return new HashSet<>(jdbcTemplate.queryForList("SELECT place_id FROM statistics_home_suggestion_dismissals WHERE user_id = ?", Long.class, user.getId()));
    }

    public void dismissHomeSuggestion(User user, long placeId) {
        jdbcTemplate.update("""
                            INSERT INTO statistics_home_suggestion_dismissals (user_id, place_id, dismissed_at) VALUES (?, ?, now())
                            ON CONFLICT (user_id, place_id) DO NOTHING
                            """, user.getId(), placeId);
    }
}
