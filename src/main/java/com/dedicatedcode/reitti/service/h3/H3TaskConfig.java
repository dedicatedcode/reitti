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

        @SuppressWarnings("DataFlowIssue")
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
                    int batchSize = 10000;

                    jdbcTemplate.execute("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_source_points_h3_cell ON raw_source_points (h3_cell) WHERE h3_cell IS NOT NULL");

                    boolean hasLocationPoints = Boolean.TRUE.equals(
                            jdbcTemplate.queryForObject("SELECT EXISTS(SELECT 1 FROM raw_location_points WHERE h3_cell IS NOT NULL)", Boolean.class));
                    boolean hasSourcePoints = Boolean.TRUE.equals(
                            jdbcTemplate.queryForObject("SELECT EXISTS(SELECT 1 FROM raw_source_points WHERE h3_cell IS NOT NULL)", Boolean.class));
                    boolean hasCellStats = Boolean.TRUE.equals(
                            jdbcTemplate.queryForObject("SELECT EXISTS(SELECT 1 FROM h3_cells_stats)", Boolean.class));
                    boolean hasAreaStats = Boolean.TRUE.equals(
                            jdbcTemplate.queryForObject("SELECT EXISTS(SELECT 1 FROM h3_area_coverage_stats)", Boolean.class));

                    if (!hasLocationPoints && !hasSourcePoints && !hasCellStats && !hasAreaStats) {
                        log.info("No H3 cells to purge.");
                        return;
                    }
                    log.info("Dropping H3 partial indexes for faster bulk update...");
                    jdbcTemplate.execute("DROP INDEX CONCURRENTLY IF EXISTS idx_points_h3_time");
                    jdbcTemplate.execute("DROP INDEX CONCURRENTLY IF EXISTS idx_source_points_h3_cell");

                    purgeTable("raw_location_points", batchSize);
                    purgeTable("raw_source_points", batchSize);

                    if (hasCellStats) {
                        log.info("Truncating h3_cells_stats...");
                        jdbcTemplate.execute("TRUNCATE TABLE h3_cells_stats");
                        log.info("h3_cells_stats truncated.");
                    }
                    if (hasAreaStats) {
                        log.info("Truncating h3_area_coverage_stats...");
                        jdbcTemplate.execute("TRUNCATE TABLE h3_area_coverage_stats");
                        log.info("h3_area_coverage_stats truncated.");
                    }

                    log.info("Rebuilding H3 partial indexes concurrently...");
                    jdbcTemplate.execute("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_points_h3_time ON raw_location_points (h3_cell) WHERE h3_cell IS NOT NULL");
                    jdbcTemplate.execute("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_source_points_h3_cell ON raw_source_points (h3_cell) WHERE h3_cell IS NOT NULL");
                    log.info("H3 cleanup completed successfully.");
                } catch (Exception e) {
                    log.error("Failed to execute H3 cleanup task", e);
                }
            });
        }

        private void purgeTable(String tableName, int batchSize) {
            int cleared = 0;
            int updatedRows;
            long startedAt = System.currentTimeMillis();

            log.info("Purging {} h3_cells...", tableName);
            do {
                long batchStart = System.currentTimeMillis();
                updatedRows = jdbcTemplate.update(
                        "UPDATE " + tableName + " SET h3_cell = NULL " +
                                "WHERE id IN (SELECT id FROM " + tableName + " WHERE h3_cell IS NOT NULL LIMIT ?)",
                        batchSize);
                cleared += updatedRows;
                long elapsed = System.currentTimeMillis() - batchStart;

                if (updatedRows > 0) {
                    log.info("{}: cleared {} cells ({} in last batch, {}ms)", tableName, cleared, updatedRows, elapsed);
                }
            } while (updatedRows > 0);
            long totalElapsed = System.currentTimeMillis() - startedAt;
            log.info("{} purge complete. Total cleared: {} in {}ms", tableName, cleared, totalElapsed);
        }
    }
}
