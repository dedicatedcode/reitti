package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.MergeVisitEvent;
import com.dedicatedcode.reitti.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;

@Component
public class VisitMergingRunner  {

    private static final Logger logger = LoggerFactory.getLogger(VisitMergingRunner.class);

    private final UserService userService;
    private final TripDetectionService tripDetectionService;
    private final RabbitTemplate rabbitTemplate;
    @Value("${reitti.process-visits-on-startup:false}")
    private boolean processVisitsOnStartup;



    public VisitMergingRunner(UserService userService,
                              TripDetectionService tripDetectionService, RabbitTemplate rabbitTemplate) {
        this.userService = userService;
        this.tripDetectionService = tripDetectionService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(cron = "${reitti.process-visits-trips.schedule}")
    public void run() {
        if (processVisitsOnStartup) {
            userService.getAllUsers().forEach(user -> {
                logger.info("Schedule visit merging process for user {}", user.getUsername());
                rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.MERGE_VISIT_ROUTING_KEY, new MergeVisitEvent(user.getId(), null, null));
                rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.MERGE_TRIP_ROUTING_KEY, new MergeVisitEvent(user.getId(), null, null));
            });
        } else {
            logger.info("Visit merging on startup is disabled. Set reitti.process-visits-on-startup=true to enable.");
        }
    }
}
