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
        List<String> itemNames;
        try {
            itemNames = this.storageService.getChildren("/memories");
        } catch (Exception e) {
            log.error("Failed to get memory directories for cleanup", e);
            return;
        }
        
        for (String itemName : itemNames) {
            try {
                boolean exists = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM memory WHERE id = ?::int", Integer.class, itemName) > 0;
                if (!exists) {
                    String pathToDelete = "/memories/" + itemName;
                    this.storageService.remove(pathToDelete);
                    log.info("deleted path [{}] since memory [{}] does not exists", pathToDelete, itemName);
                } else {
                    try {
                        this.storageService.getChildren("/memories/" + itemName).forEach(s -> {
                            try {
                                // Check if the file is referenced in any memory_block_image_gallery images column
                                boolean isReferenced = this.jdbcTemplate.queryForObject(
                                        "SELECT COUNT(*) FROM memory_block_image_gallery WHERE images::text LIKE ? AND block_id IN (SELECT id FROM memory_block WHERE memory_id = ?::int)",
                                        Integer.class,
                                        "%" + s + "%",
                                        itemName
                                ) > 0;
                                
                                if (!isReferenced) {
                                    String pathToDelete = "/memories/" + itemName + "/" + s;
                                    this.storageService.remove(pathToDelete);
                                    log.info("deleted file [{}] since it is not referenced in any memory block image gallery", pathToDelete);
                                }
                            } catch (Exception e) {
                                log.error("Failed to process file [{}] in memory [{}] during cleanup", s, itemName, e);
                            }
                        });
                    } catch (Exception e) {
                        log.error("Failed to get files for memory [{}] during cleanup", itemName, e);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to process memory [{}] during cleanup", itemName, e);
            }
        }
    }
}
