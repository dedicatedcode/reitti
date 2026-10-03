package com.dedicatedcode.reitti.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.function.Supplier;

@Service
public class JdbcPropertyService {
    private static final Logger log = LoggerFactory.getLogger(JdbcPropertyService.class);
    private final JdbcTemplate jdbcTemplate;

    public JdbcPropertyService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<String> getProperty(String key) {
        return Optional.ofNullable(jdbcTemplate.query(
                "SELECT property_value ->> 'value' FROM reitti_properties WHERE property_key = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                key));
    }

    public void setProperty(String key, String value) {
        jdbcTemplate.update("""
                        INSERT INTO reitti_properties (property_key, property_value)
                        VALUES (?, jsonb_build_object('value', ?::text))
                        ON CONFLICT (property_key) DO UPDATE SET property_value = EXCLUDED.property_value
                        """,
                key, value);
    }

    public String getOrCreateProperty(String key, Supplier<String> valueSupplier) {
        Optional<String> existing = getProperty(key);
        if (existing.isPresent()) {
            return existing.get();
        }
        String value = valueSupplier.get();
        setProperty(key, value);
        log.info("Created property [{}]", key);
        return value;
    }
}
