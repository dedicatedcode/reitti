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
import java.util.List;
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
        places.forEach(place -> {
            Optional<ZoneId> zoneId = engine.query(place.getLatitudeCentroid(), place.getLongitudeCentroid());
            zoneId.ifPresent(id -> {
                log.debug("Zone ID [{}] found in for [{}]", id, place);
                //instead of saving every place on its own, add a bulk update method in this class which only updates the timezone column for a given id AI!
                this.significantPlaceJdbcService.update(place.withTimezone(id));
            });
        });
    }

    public Optional<ZoneId> getTimezone(SignificantPlace place) {
        return this.engine.query(place.getLatitudeCentroid(), place.getLongitudeCentroid());
    }
}
