package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.PlaceVisitStatistics;
import com.dedicatedcode.reitti.model.Page;
import com.dedicatedcode.reitti.model.PageRequest;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.SignificantPlaceJdbcService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class PlaceService {

    private final SignificantPlaceJdbcService significantPlaceJdbcService;
    private final JdbcTemplate jdbcTemplate;

    public PlaceService(SignificantPlaceJdbcService significantPlaceJdbcService, JdbcTemplate jdbcTemplate) {
        this.significantPlaceJdbcService = significantPlaceJdbcService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Page<SignificantPlace> getPlacesForUser(User user, PageRequest pageable) {
        return significantPlaceJdbcService.findByUser(user, pageable);
    }

    public PlaceVisitStatistics getVisitStatisticsForPlace(User user, Long placeId) {
        String sql = """
            SELECT 
                COUNT(*) as total_visits,
                MIN(start_time) as first_visit,
                MAX(start_time) as last_visit
            FROM processed_visits pv
            JOIN significant_places sp ON pv.place_id = sp.id
            WHERE sp.id = ? AND sp.user_id = ?
            """;

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            long totalVisits = rs.getLong("total_visits");
            Instant firstVisit = rs.getTimestamp("first_visit") != null ? 
                rs.getTimestamp("first_visit").toInstant() : null;
            Instant lastVisit = rs.getTimestamp("last_visit") != null ? 
                rs.getTimestamp("last_visit").toInstant() : null;
            
            return new PlaceVisitStatistics(totalVisits, firstVisit, lastVisit);
        }, placeId, user.getId());
    }

}

