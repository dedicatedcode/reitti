package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.LocationProcessEvent;
import com.dedicatedcode.reitti.model.RawLocationPoint;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.ImportStateHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RawLocationPointProcessingTrigger {
    private static final Logger log = LoggerFactory.getLogger(RawLocationPointProcessingTrigger.class);
    private static final int BATCH_SIZE = 100;

    private final ImportStateHolder stateHolder;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final UserJdbcService userJdbcService;
    private final RabbitTemplate rabbitTemplate;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public RawLocationPointProcessingTrigger(ImportStateHolder stateHolder,
                                             RawLocationPointJdbcService rawLocationPointJdbcService,
                                             UserJdbcService userJdbcService,
                                             RabbitTemplate rabbitTemplate) {
        this.stateHolder = stateHolder;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.userJdbcService = userJdbcService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(cron = "${reitti.process-data.schedule}")
    public void start() {
        if (isRunning.get()) {
            log.warn("Processing is already running, wil skip this run");
            return;
        }

        if (stateHolder.isImportRunning()) {
            log.warn("Data Import is currently running, wil skip this run");
            return;
        }
        isRunning.set(true);
        for (User user : userJdbcService.findAll()) {
            List<RawLocationPoint> allUnprocessedPoints = rawLocationPointJdbcService.findByUserAndProcessedIsFalseOrderByTimestamp(user);

            log.debug("Found [{}] unprocessed points for user [{}]", allUnprocessedPoints.size(), user.getId());
            int i = 0;
            while (i * BATCH_SIZE < allUnprocessedPoints.size()) {
                int fromIndex = i * BATCH_SIZE;
                int toIndex = Math.min((i + 1) * BATCH_SIZE, allUnprocessedPoints.size());
                List<RawLocationPoint> currentPoints = allUnprocessedPoints.subList(fromIndex, toIndex);
                Instant earliest = currentPoints.getFirst().getTimestamp();
                Instant latest = currentPoints.getLast().getTimestamp();
                log.debug("Scheduling stay detection event for user [{}] and points between [{}] and [{}]", user.getId(), earliest, latest);
                this.rabbitTemplate
                        .convertAndSend(RabbitMQConfig.EXCHANGE_NAME,
                                RabbitMQConfig.STAY_DETECTION_ROUTING_KEY,
                                new LocationProcessEvent(user.getUsername(), earliest, latest));
                currentPoints.forEach(RawLocationPoint::markProcessed);
                rawLocationPointJdbcService.bulkUpdateProcessedStatus(currentPoints);
                i++;
            }
        }
        isRunning.set(false);
    }

}
