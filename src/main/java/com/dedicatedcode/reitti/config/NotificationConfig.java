package com.dedicatedcode.reitti.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Beans used for delivering notifications to external channels. They are kept separate from the
 * shared application {@code restTemplate} so a slow notification endpoint can never block other
 * integration calls.
 */
@Configuration
public class NotificationConfig {

    @Bean
    public RestTemplate notificationRestTemplate(
            @Value("${reitti.notifications.external.timeout-seconds:10}") long timeoutSeconds) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return new RestTemplate(requestFactory);
    }

    /**
     * Dedicated, bounded executor for external channel delivery. A failing or hanging channel must
     * never affect the request that produced the notification or the other channels.
     */
    @Bean(name = "notificationExecutor")
    public ThreadPoolTaskExecutor notificationExecutor(
            @Value("${reitti.notifications.external.dispatch-threads:4}") int threads) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int poolSize = Math.max(1, threads);
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("notification-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }
}
