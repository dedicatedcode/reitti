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
    @ConditionalOnProperty(name = "h3.enabled", havingValue = "true", matchIfMissing = true)
    public static class H3EnabledConfiguration {
        @Bean("h3IndexUpdateJob")
        public JobDetail h3IndexUpdateJobDetail() {
            return JobBuilder.newJob(H3DatabaseLifecycleManager.class)
                    .withIdentity(UUID.randomUUID().toString(), JOB_GROUP)
                    .storeDurably().build();
        }

        @Bean
        public Trigger h3StartupTrigger(@Qualifier("h3IndexUpdateJob") JobDetail jobDetail) {
            return TriggerBuilder.newTrigger()
                    .forJob(jobDetail)
                    .withIdentity(UUID.randomUUID().toString(), JOB_GROUP)
                    .startAt(DateBuilder.futureDate(10, DateBuilder.IntervalUnit.SECOND))
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                                          .withMisfireHandlingInstructionFireNow())
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
    @ConditionalOnProperty(name = "reitti.h3.enabled", havingValue = "false")
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

                    // Bulk-delete all matched jobs.
                    // This automatically drops all their corresponding triggers simultaneously.
                    scheduler.deleteJobs(new ArrayList<>(orphanedKeys));

                    log.info("Successfully cleared all persistent database records for group '{}'.", JOB_GROUP);
                }
            } catch (SchedulerException e) {
                log.error("Failed to execute group purge for disabled H3 feature", e);
            }
            try {
                String structuralSwapSql = """
                        ALTER TABLE raw_location_points DROP COLUMN IF EXISTS h3_res9, ADD COLUMN h3_res9 BIGINT NULL;
                        ALTER TABLE raw_source_points DROP COLUMN IF EXISTS h3_res9, ADD COLUMN h3_res9 BIGINT NULL;
                        """;

                log.info("Executing atomic column metadata swap...");
                jdbcTemplate.execute(structuralSwapSql);
                log.info("Column swapped successfully. Data has been entirely purged.");

                String rebuildIndexSql = """
                        CREATE INDEX CONCURRENTLY idx_points_h3_res9 ON raw_location_points (h3_res9)
                        WHERE h3_res9 IS NOT NULL;
                        """;

                log.info("Rebuilding partial index concurrently in the background...");
                jdbcTemplate.execute(rebuildIndexSql);
                log.info("H3 structural maintenance task completed successfully.");
            } catch (Exception e) {
                log.error("Failed to execute structural maintenance task for disabled H3 feature", e);

            }
        }
    }
}
