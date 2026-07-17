package com.dedicatedcode.reitti.service.h3;

import org.quartz.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
@ConditionalOnProperty(name = "reitti.h3.enabled", havingValue = "true")
public class H3TaskConfig {

    @Bean("h3IndexUpdateJob")
    public JobDetail h3IndexUpdateJobDetail() {
        return JobBuilder.newJob(H3DatabaseLifecycleManager.class)
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger h3StartupTrigger(@Qualifier("h3IndexUpdateJob") JobDetail jobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(jobDetail)
                .withIdentity(UUID.randomUUID().toString())
                .startAt(DateBuilder.futureDate(10, DateBuilder.IntervalUnit.SECOND))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                                      .withMisfireHandlingInstructionFireNow())
                .build();
    }

    @Bean
    public Trigger h3ScheduledTrigger(@Qualifier("h3IndexUpdateJob") JobDetail jobDetail, @Value("${reitti.h3.cron-schedule}") String cronSchedule) {
        return TriggerBuilder.newTrigger()
                .forJob(jobDetail)
                .withIdentity(UUID.randomUUID().toString())
                .withSchedule(CronScheduleBuilder.cronSchedule(cronSchedule)
                                      .withMisfireHandlingInstructionDoNothing())
                .build();
    }
}
