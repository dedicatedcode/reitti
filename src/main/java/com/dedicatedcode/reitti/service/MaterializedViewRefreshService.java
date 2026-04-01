package com.dedicatedcode.reitti.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MaterializedViewRefreshService {
    private final JdbcTemplate jdbcTemplate;

    public MaterializedViewRefreshService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "${reitti.process-data.refresh-views.schedule}")
    public void refreshRawLocationMaterializedView() {
        this.jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY location_daily_summary");
        this.jdbcTemplate.execute("ANALYZE location_daily_summary;");
    }
}
