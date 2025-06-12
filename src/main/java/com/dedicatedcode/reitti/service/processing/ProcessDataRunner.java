package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProcessDataRunner {

    private static final Logger logger = LoggerFactory.getLogger(ProcessDataRunner.class);

    private final UserService userService;
    private final RabbitTemplate rabbitTemplate;

    public ProcessDataRunner(UserService userService,
                             RabbitTemplate rabbitTemplate) {
        this.userService = userService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(cron = "${reitti.process-data.schedule}")
    public void run() {
//        userService.getAllUsers().forEach(user -> {
//            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.MERGE_VISIT_ROUTING_KEY, new MergeVisitEvent(user.getUsername(), null, null));
//        });
    }
}
