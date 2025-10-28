package com.dedicatedcode.reitti.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MemoryCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(MemoryCleanupJob.class);

    private final StorageService storageService;
    private final JdbcTemplate jdbcTemplate;

    public MemoryCleanupJob(StorageService storageService, JdbcTemplate jdbcTemplate) {
        this.storageService = storageService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "${reitti.storage.cleanup.cron}")
    public void cleanUp() {
        List<String> itemNames = this.storageService.getDirectories("/memories");
        for (String itemName : itemNames) {

            boolean exists = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM memory WHERE id = ?::int", Integer.class, itemName) > 0;
            if (!exists) {
                this.storageService.remove("/memories/" + itemName);
            }
        }
    }
}
