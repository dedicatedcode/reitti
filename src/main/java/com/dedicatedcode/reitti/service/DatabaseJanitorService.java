package com.dedicatedcode.reitti.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DatabaseJanitorService {
    private static final Logger log = LoggerFactory.getLogger(DatabaseJanitorService.class);
    private final JdbcTemplate jdbcTemplate;

    public DatabaseJanitorService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "${reitti.db-janitor.schedule}")
    public void runJanitor() {
        log.info("Running Janitor");
        long start = System.currentTimeMillis();
        jdbcTemplate.update("DELETE FROM api_token_usages WHERE at < now() - interval '1 week';");
        log.info("Clearing old api-token-usages in {}ms", System.currentTimeMillis() - start);
        jdbcTemplate.execute("VACUUM ANALYZE;");
    }
}
