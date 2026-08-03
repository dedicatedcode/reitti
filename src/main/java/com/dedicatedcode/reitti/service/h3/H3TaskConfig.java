package com.dedicatedcode.reitti.service.h3;

import jakarta.annotation.PostConstruct;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

@Configuration
public class H3TaskConfig {
    private static final Logger log = LoggerFactory.getLogger(H3TaskConfig.class);
    private static final String JOB_GROUP = "h3-indexing";

    @Configuration
    @ConditionalOnProperty(name = "reitti.h3.enabled", havingValue = "true")
    public static class H3EnabledConfiguration {
        @Bean("h3CellUpdateTask")
        public JobDetail h3CellUpdateJobDetail() {
            return JobBuilder.newJob(H3CellUpdateJob.class)
                    .withIdentity("h3-cell-update", JOB_GROUP)
                    .storeDurably()
                    .withDescription("H3 Cell Update Job")
                    .build();
        }

        @Bean("h3IndexUpdateJob")
        public JobDetail h3IndexUpdateJobDetail() {
            return JobBuilder.newJob(H3DatabaseLifecycleManager.class)
                    .withIdentity("h3-index-update", JOB_GROUP)
                    .withDescription("H3 Database Lifecycle Manager")
                    .storeDurably()
                    .build();
        }

        @Bean("h3RecalculationJob")
        public JobDetail h3RecalculationJobDetail() {
            return JobBuilder.newJob(H3RecalculationJob.class)
                    .withIdentity("h3-recalculation", JOB_GROUP)
                    .storeDurably()
                    .build();
        }

        @Bean
        public Trigger h3StartupTrigger(@Qualifier("h3IndexUpdateJob") JobDetail jobDetail) {
            return TriggerBuilder.newTrigger()
                    .forJob(jobDetail)
                    .withIdentity("h3-startup-trigger", JOB_GROUP)
                    .startNow()
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                                          .withMisfireHandlingInstructionNowWithExistingCount())
                    .withPriority(100)
                    .build();
        }

        @Bean
        public Trigger h3ScheduledTrigger(@Qualifier("h3IndexUpdateJob") JobDetail jobDetail, @Value("${reitti.h3.cron-schedule}") String cronSchedule) {
            return TriggerBuilder.newTrigger()
                    .forJob(jobDetail)
                    .withIdentity("h3-cron-trigger", JOB_GROUP)
                    .withSchedule(CronScheduleBuilder.cronSchedule(cronSchedule)
                                          .withMisfireHandlingInstructionDoNothing())
                    .build();
        }
    }

    @Configuration
    @ConditionalOnProperty(name = "reitti.h3.enabled", havingValue = "false", matchIfMissing = true)
    public static class H3DisabledHousekeeper {

        private final Scheduler scheduler;
        private final JdbcTemplate jdbcTemplate;

        public H3DisabledHousekeeper(Scheduler scheduler, JdbcTemplate jdbcTemplate) {
            this.scheduler = scheduler;
            this.jdbcTemplate = jdbcTemplate;
        }

        @PostConstruct
        public void cleanupH3Leftovers() {
            Thread.ofVirtual().name("h3-cleanup").start(() -> {
                try {
                    GroupMatcher<JobKey> matcher = GroupMatcher.jobGroupEquals(JOB_GROUP);
                    Set<JobKey> orphanedKeys = scheduler.getJobKeys(matcher);

                    if (!orphanedKeys.isEmpty()) {
                        log.warn("H3 Feature is disabled. Purging {} orphaned dynamic H3 job(s) from group '{}'...",
                                 orphanedKeys.size(), JOB_GROUP);
                        scheduler.deleteJobs(new ArrayList<>(orphanedKeys));
                        log.info("Successfully cleared all persistent database records for group '{}'.", JOB_GROUP);
                    }
                } catch (SchedulerException e) {
                    log.error("Failed to execute group purge for disabled H3 feature", e);
                }

                try {
                    log.info("H3 disabled. Purging H3 cell data in background...");
                    jdbcTemplate.execute("ALTER TABLE raw_location_points ADD COLUMN IF NOT EXISTS h3_cell BIGINT NULL");
                    jdbcTemplate.execute("ALTER TABLE raw_source_points ADD COLUMN IF NOT EXISTS h3_cell BIGINT NULL");

                    int batchSize = 10000;

                    Long rlpCount = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM raw_location_points WHERE h3_cell IS NOT NULL", Long.class);
                    Long rspCount = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM raw_source_points WHERE h3_cell IS NOT NULL", Long.class);
                    
                    log.info("Dropping H3 partial index for faster bulk update...");
                    jdbcTemplate.execute("DROP INDEX CONCURRENTLY IF EXISTS idx_points_h3_cell");

                    log.info("Found H3 cells to purge: {} in raw_location_points, {} in raw_source_points",
                            rlpCount, rspCount);

                    purgeTable("raw_location_points", rlpCount != null ? rlpCount : 0, batchSize);

                    purgeTable("raw_source_points", rspCount != null ? rspCount : 0, batchSize);

                    String rebuildIndexSql = """
                            CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_points_h3_cell ON raw_location_points (h3_cell)
                            WHERE h3_cell IS NOT NULL;
                            """;

                    log.info("Rebuilding partial H3 index concurrently...");
                    jdbcTemplate.execute(rebuildIndexSql);
                    log.info("H3 cleanup completed successfully.");
                } catch (Exception e) {
                    log.error("Failed to execute H3 cleanup task", e);
                }
            });
        }

        private void purgeTable(String tableName, long totalToClear, int batchSize) {
            if (totalToClear == 0) {
                log.info("{}: no H3 cells to purge.", tableName);
                return;
            }

            int cleared = 0;
            int batchNum = 0;
            long batchDurationMs = 0;
            int updatedRows;
            long startedAt = System.currentTimeMillis();

            log.info("Purging {} h3_cells ({} rows to clear)...", tableName, totalToClear);
            do {
                batchNum++;
                long batchStart = System.currentTimeMillis();
                updatedRows = jdbcTemplate.update(
                        "UPDATE " + tableName + " SET h3_cell = NULL " +
                                "WHERE id IN (SELECT id FROM " + tableName + " WHERE h3_cell IS NOT NULL LIMIT ?)",
                        batchSize);
                cleared += updatedRows;
                long elapsed = System.currentTimeMillis() - batchStart;

                if (batchNum == 1 && updatedRows > 0) {
                    batchDurationMs = elapsed;
                }

                if (updatedRows > 0) {
                    long remaining = totalToClear - cleared;
                    String eta = batchDurationMs > 0
                            ? formatEta(remaining * batchDurationMs / batchSize)
                            : "calculating...";
                    log.info("{}: cleared {} / {} rows ({} in last batch, {}ms). ETA: {}",
                            tableName, cleared, totalToClear, updatedRows, elapsed, eta);
                }
            } while (updatedRows > 0);
            long totalElapsed = System.currentTimeMillis() - startedAt;
            log.info("{} purge complete. Total cleared: {} in {}ms", tableName, cleared, totalElapsed);
        }

        private String formatEta(long ms) {
            if (ms < 1000) return "<1s";
            long seconds = ms / 1000;
            if (seconds < 60) return seconds + "s";
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        }
    }
}
