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
        List<String> itemNames = this.storageService.getChildren("/memories");
        for (String itemName : itemNames) {

            boolean exists = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM memory WHERE id = ?::int", Integer.class, itemName) > 0;
            if (!exists) {
                String pathToDelete = "/memories/" + itemName;
                this.storageService.remove(pathToDelete);
                log.info("deleted path [{}] since memory [{}] does not exists", pathToDelete, itemName);
            } else {
                this.storageService.getChildren("/memories/" + itemName).forEach(s -> {
                    // Check if the file is referenced in any memory_block_image_gallery images column
                    boolean isReferenced = this.jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM memory_block_image_gallery WHERE images::text LIKE ?", 
                        Integer.class, 
                        "%" + s + "%"
                    ) > 0;
                    
                    if (!isReferenced) {
                        String pathToDelete = "/memories/" + itemName + "/" + s;
                        this.storageService.remove(pathToDelete);
                        log.info("deleted file [{}] since it is not referenced in any memory block image gallery", pathToDelete);
                    }
                });

            }
        }
    }
}
