package com.dedicatedcode.reitti.repository;

import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.ResultSet;
import java.sql.Timestamp;

/**
 * Shared SQL plumbing for the chunked point streams used by
 * {@link RawLocationPointJdbcService} and {@link PreviewRawLocationPointJdbcService}.
 */
final class PointStreamSupport {

    private PointStreamSupport() {
    }

    static final ResultSetExtractor<RawLocationPointStream.Stats> STATS_EXTRACTOR = PointStreamSupport::extractStats;

    static RawLocationPointStream.Stats extractStats(ResultSet rs) throws java.sql.SQLException {
        rs.next();
        Timestamp minTs = rs.getTimestamp("min_ts");
        Timestamp maxTs = rs.getTimestamp("max_ts");
        return new RawLocationPointStream.Stats(rs.getLong("point_count"),
                minTs != null ? minTs.toInstant() : null,
                maxTs != null ? maxTs.toInstant() : null);
    }

    /**
     * Appends the keyset-cursor predicate for (timestamp, id) to the given SQL
     * builder when resuming after a chunk boundary. Both point tables are
     * aliased as "rlp".
     */
    static void appendKeysetPredicate(StringBuilder sql, Timestamp afterTimestamp) {
        if (afterTimestamp != null) {
            sql.append("AND (rlp.timestamp > ? OR (rlp.timestamp = ? AND rlp.id > ?)) ");
        }
    }
}
