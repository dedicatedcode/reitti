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
                    .withIdentity(UUID.randomUUID().toString(), JOB_GROUP)
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
                    .withIdentity(UUID.randomUUID().toString(), JOB_GROUP)
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
            try {
                // Create a matcher pointing specifically to your custom group
                GroupMatcher<JobKey> matcher = GroupMatcher.jobGroupEquals(JOB_GROUP);

                // Query the persistent database for any keys under this group
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
                log.info("Purging H3 data in batches...");
                jdbcTemplate.execute("ALTER TABLE raw_location_points ADD COLUMN IF NOT EXISTS h3_cell BIGINT NULL");
                jdbcTemplate.execute("ALTER TABLE raw_source_points ADD COLUMN IF NOT EXISTS h3_cell BIGINT NULL");

                int batchSize = 50000;
                int updatedRows;
                do {
                    updatedRows = jdbcTemplate.update(
                            "UPDATE raw_location_points SET h3_cell = NULL " +
                                    "WHERE id IN (SELECT id FROM raw_location_points WHERE h3_cell IS NOT NULL LIMIT ?)",
                            batchSize);
                } while (updatedRows > 0);

                do {
                    updatedRows = jdbcTemplate.update(
                            "UPDATE raw_source_points SET h3_cell = NULL " +
                                    "WHERE id IN (SELECT id FROM raw_source_points WHERE h3_cell IS NOT NULL LIMIT ?)",
                            batchSize);
                } while (updatedRows > 0);

                log.info("H3 data purged successfully.");

                String rebuildIndexSql = """
                        CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_points_h3_cell ON raw_location_points (h3_cell)
                        WHERE h3_cell IS NOT NULL;
                        """;

                log.info("Rebuilding partial index concurrently in the background...");
                jdbcTemplate.execute(rebuildIndexSql);
                log.info("H3 structural maintenance task completed successfully.");
            } catch (Exception e) {
                log.error("Failed to execute structural maintenance task for disabled H3 feature", e);
            }        }
    }
}
