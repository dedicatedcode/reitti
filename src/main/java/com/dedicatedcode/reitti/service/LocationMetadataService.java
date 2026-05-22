package com.dedicatedcode.reitti.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class LocationMetadataService {
    private static final Logger log = LoggerFactory.getLogger(LocationMetadataService.class);
    private final JdbcTemplate jdbcTemplate;

    public LocationMetadataService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }


}
