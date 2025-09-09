package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.repository.SignificantPlaceJdbcService;
import jakarta.annotation.PostConstruct;
import net.iakovlev.timeshape.TimeZoneEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class GeoLocationTimezoneService {
    private static final Logger log = LoggerFactory.getLogger(GeoLocationTimezoneService.class);
    private final TimeZoneEngine engine;
    private final SignificantPlaceJdbcService significantPlaceJdbcService;
    private final JdbcTemplate jdbcTemplate;

    public GeoLocationTimezoneService(SignificantPlaceJdbcService significantPlaceJdbcService, JdbcTemplate jdbcTemplate) {
        this.significantPlaceJdbcService = significantPlaceJdbcService;
        this.jdbcTemplate = jdbcTemplate;
        this.engine = TimeZoneEngine.initialize();
    }

    @PostConstruct
    public void init() {
        List<SignificantPlace> places = significantPlaceJdbcService.findWithMissingTimezone();
        log.info("Searching for SignificantPlaces without Timezone data. Found [{}]", places.size());
        Map<Long, ZoneId> foundTimezones = new HashMap<>();
        places.forEach(place -> {
            Optional<ZoneId> zoneId = engine.query(place.getLatitudeCentroid(), place.getLongitudeCentroid());
            zoneId.ifPresent(id -> {
                log.debug("Zone ID [{}] found in for [{}]", id, place);
                foundTimezones.put(place.getId(), id);
            });
        });
        
        if (!foundTimezones.isEmpty()) {
            bulkUpdateTimezones(foundTimezones);
        }
    }

    private void bulkUpdateTimezones(Map<Long, ZoneId> timezoneUpdates) {
        String sql = "UPDATE significant_places SET timezone = ? WHERE id = ?";
        
        jdbcTemplate.batchUpdate(sql, timezoneUpdates.entrySet(), timezoneUpdates.size(),
            (ps, entry) -> {
                ps.setString(1, entry.getValue().getId());
                ps.setLong(2, entry.getKey());
            });
        
        log.info("Bulk updated timezones for [{}] significant places", timezoneUpdates.size());
    }

    public Optional<ZoneId> getTimezone(SignificantPlace place) {
        Optional<ZoneId> zoneId = this.engine.query(place.getLatitudeCentroid(), place.getLongitudeCentroid());
        if (zoneId.isEmpty()) {
            log.warn("Unable to find timezone for place [{}]", place);
        }
        return zoneId;
    }
}
