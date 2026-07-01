package com.dedicatedcode.reitti.config;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

@Configuration
public class QuartzConfig {
    @Bean
    public SchedulerFactoryBeanCustomizer schedulerFactoryBeanCustomizer(ApplicationContext context) {
        return schedulerFactoryBean -> {
            AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory(context);
            schedulerFactoryBean.setJobFactory(jobFactory);
        };
    }


    public static class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory {
        private final ApplicationContext context;

        public AutowiringSpringBeanJobFactory(ApplicationContext context) {
            this.context = context;
        }

        @Override
        protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
            Object job = super.createJobInstance(bundle);
            context.getAutowireCapableBeanFactory().autowireBean(job);
            return job;
        }
    }
}
