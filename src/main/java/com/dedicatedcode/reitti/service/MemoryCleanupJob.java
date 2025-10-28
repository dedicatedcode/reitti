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
                    //there is a jsonb column  named images in the memory_block_image_gallery table which contains image urls which contain the string s
                    // the format of the data is [{"caption": null, "imageUrl": "/api/v1/photos/reitti/memories/24/c89eb431-c324-419d-ba5a-944ae0dd6db6.jpg", "integration": "immich", "integrationId": "c89eb431-c324-419d-ba5a-944ae0dd6db6"}, {"caption": null, "imageUrl": "/api/v1/photos/reitti/memories/24/8d26f710-ca30-4cc5-9019-6864f6801124.jpg", "integration": "immich", "integrationId": "8d26f710-ca30-4cc5-9019-6864f6801124"}, {"caption": null, "imageUrl": "/api/v1/photos/reitti/memories/24/7272d06b-14a9-4e51-9350-79592de80d9e.jpg", "integration": "immich", "integrationId": "7272d06b-14a9-4e51-9350-79592de80d9e"}, {"caption": null, "imageUrl": "/api/v1/photos/reitti/memories/24/99226122-140b-430a-8ea3-017ca5ec9911.jpg", "integration": "immich", "integrationId": "99226122-140b-430a-8ea3-017ca5ec9911"}]
                    // if s is not contained in the column, it should be deleted. AI!
                        }
                );

            }
        }
    }
}
