package com.dedicatedcode.reitti.service.h3;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(name = "reitti.h3.enabled", havingValue = "true")
public class CoveragePointStatsService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final JdbcTemplate jdbcTemplate;
    private final RocksDBH3Service rocksDBService;
    private final int visitedCellMinPoints;
    private final Duration visitedCellMinDwell;
    private final Map<CacheKey, CachedStats> cache = new ConcurrentHashMap<>();

    public CoveragePointStatsService(JdbcTemplate jdbcTemplate,
                                     RocksDBH3Service rocksDBService,
                                     @Value("${reitti.coverage.visited-cell.min-points:10}") int visitedCellMinPoints,
                                     @Value("${reitti.coverage.visited-cell.min-dwell:PT10M}") Duration visitedCellMinDwell) {
        this.jdbcTemplate = jdbcTemplate;
        this.rocksDBService = rocksDBService;
        this.visitedCellMinPoints = Math.max(1, visitedCellMinPoints);
        this.visitedCellMinDwell = visitedCellMinDwell == null ? Duration.ofMinutes(10) : visitedCellMinDwell;
    }

    public AreaPointStats getStats(User user, Device device) {
        if (!rocksDBService.isAvailable()) {
            return AreaPointStats.EMPTY;
        }
        CacheKey key = new CacheKey(user.getId(), device == null ? null : device.id(), rocksDBService.getActiveVersion());
        Instant now = Instant.now();
        CachedStats cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.stats();
        }
        AreaPointStats stats = compute(user, device);
        cache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        cache.put(key, new CachedStats(stats, now.plus(CACHE_TTL)));
        return stats;
    }

    private AreaPointStats compute(User user, Device device) {
        Map<Long, CellStats> cellStats = new HashMap<>();
        if (device == null) {
            jdbcTemplate.query("""
                    SELECT h3_cell, COUNT(*) AS point_count, MIN(timestamp) AS first_visited, MAX(timestamp) AS last_visited
                    FROM v_source_stream
                    WHERE user_id = ? AND h3_cell IS NOT NULL
                    GROUP BY h3_cell
                    """, rs -> {
                addParentCellCounts(rs.getLong("h3_cell"), rs.getLong("point_count"),
                        rs.getTimestamp("first_visited").toInstant(),
                        rs.getTimestamp("last_visited").toInstant(), cellStats);
            }, user.getId());
        } else {
            jdbcTemplate.query("""
                    SELECT h3_cell, COUNT(*) AS point_count, MIN(timestamp) AS first_visited, MAX(timestamp) AS last_visited
                    FROM raw_source_points
                    WHERE user_id = ? AND device_id = ? AND status != 1 AND invalid IS FALSE AND h3_cell IS NOT NULL
                    GROUP BY h3_cell
                    """, rs -> {
                addParentCellCounts(rs.getLong("h3_cell"), rs.getLong("point_count"),
                        rs.getTimestamp("first_visited").toInstant(),
                        rs.getTimestamp("last_visited").toInstant(), cellStats);
            }, user.getId(), device.id());
        }

        Map<Long, Long> visitedCells = new HashMap<>();
        Map<Long, Long> totalPoints = new HashMap<>();
        Map<Long, Instant> firstVisited = new HashMap<>();
        Map<Long, Instant> lastVisited = new HashMap<>();
        for (Map.Entry<Long, CellStats> entry : cellStats.entrySet()) {
            CellStats stats = entry.getValue();
            boolean visited = stats.visitedChildren() > 0;
            for (Long osmId : rocksDBService.getOsmIds(entry.getKey())) {
                totalPoints.merge(osmId, stats.count(), Long::sum);
                if (visited) {
                    visitedCells.merge(osmId, 1L, Long::sum);
                }
                if (stats.first() != null) {
                    firstVisited.merge(osmId, stats.first(), (a, b) -> a.isBefore(b) ? a : b);
                }
                if (stats.last() != null) {
                    lastVisited.merge(osmId, stats.last(), (a, b) -> a.isAfter(b) ? a : b);
                }
            }
        }
        return new AreaPointStats(visitedCells, totalPoints, firstVisited, lastVisited);
    }

    /**
     * A source cell counts as visited when the user spent enough time there (minimum number of
     * points and minimum dwell). Parent cells inherit visitedness from their children, which makes
     * exploration bubble up: being somewhere in a city also marks its district, state, country
     * and continent as discovered.
     */
    private void addParentCellCounts(long cellId, long pointCount, Instant first, Instant last,
                                     Map<Long, CellStats> target) {
        boolean sourceVisited = pointCount >= visitedCellMinPoints
                && first != null && last != null
                && !Duration.between(first, last).minus(visitedCellMinDwell).isNegative();
        for (Long parentCell : rocksDBService.getParentCells(cellId)) {
            target.merge(parentCell, new CellStats(pointCount, first, last, sourceVisited ? 1 : 0),
                    (a, b) -> new CellStats(a.count() + b.count(), min(a.first(), b.first()), max(a.last(), b.last()),
                            a.visitedChildren() + b.visitedChildren()));
        }
    }

    private static Instant min(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }

    private static Instant max(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    private record CellStats(long count, Instant first, Instant last, int visitedChildren) {
    }

    private record CacheKey(long userId, Long deviceId, String packVersion) {
    }

    private record CachedStats(AreaPointStats stats, Instant expiresAt) {
    }

    public record AreaPointStats(Map<Long, Long> visitedCells,
                                 Map<Long, Long> totalPoints,
                                 Map<Long, Instant> firstVisited,
                                 Map<Long, Instant> lastVisited) {
        public static final AreaPointStats EMPTY = new AreaPointStats(Map.of(), Map.of(), Map.of(), Map.of());

        public long visitedCells(long osmId) {
            return visitedCells.getOrDefault(osmId, 0L);
        }

        public long totalPoints(long osmId) {
            return totalPoints.getOrDefault(osmId, 0L);
        }

        public Instant firstVisited(long osmId) {
            return firstVisited.get(osmId);
        }

        public Instant lastVisited(long osmId) {
            return lastVisited.get(osmId);
        }

        public long sumVisitedCells(List<Long> osmIds) {
            return osmIds.stream().mapToLong(this::visitedCells).sum();
        }

        public long sumTotalPoints(List<Long> osmIds) {
            return osmIds.stream().mapToLong(this::totalPoints).sum();
        }
    }
}
