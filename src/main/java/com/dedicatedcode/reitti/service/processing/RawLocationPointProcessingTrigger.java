package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.LocationProcessEvent;
import com.dedicatedcode.reitti.model.RawLocationPoint;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.RawLocationPointRepository;
import com.dedicatedcode.reitti.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class RawLocationPointProcessingTrigger {
    private static final Logger log = LoggerFactory.getLogger(RawLocationPointProcessingTrigger.class);
    private static final int BATCH_SIZE = 100;

    private final RawLocationPointRepository rawLocationPointRepository;
    private final UserRepository userRepository;
    private final RabbitTemplate rabbitTemplate;
    private final JdbcTemplate jdbcTemplate;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public RawLocationPointProcessingTrigger(RawLocationPointRepository rawLocationPointRepository,
                                             UserRepository userRepository, RabbitTemplate rabbitTemplate,
                                             JdbcTemplate jdbcTemplate) {

        this.rawLocationPointRepository = rawLocationPointRepository;
        this.userRepository = userRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "${reitti.process-data.schedule}")
    public void start() {
        if (isRunning.get()) {
            log.warn("Processing is already running, wil skip this run");
        } else {
            isRunning.set(true);
            for (User user : userRepository.findAll()) {
                List<RawLocationPoint> allUnprocessedPoints = rawLocationPointRepository.findByUserAndProcessedIsFalseOrderByTimestamp(user);

                log.debug("Found [{}] unprocessed points for user [{}]", allUnprocessedPoints.size(), user.getId());
                int i = 0;
                while (i * BATCH_SIZE < allUnprocessedPoints.size()) {
                    int fromIndex = i * BATCH_SIZE;
                    int toIndex = Math.min((i + 1) * BATCH_SIZE, allUnprocessedPoints.size());
                    List<RawLocationPoint> currentPoints = allUnprocessedPoints.subList(fromIndex, toIndex);
                    Instant earliest = currentPoints.getFirst().getTimestamp();
                    Instant latest = currentPoints.getLast().getTimestamp();
                    this.rabbitTemplate
                            .convertAndSend(RabbitMQConfig.EXCHANGE_NAME,
                                    RabbitMQConfig.STAY_DETECTION_ROUTING_KEY,
                                    new LocationProcessEvent(user.getUsername(), earliest, latest));
                    currentPoints.forEach(RawLocationPoint::markProcessed);
                    bulkUpdateProcessedStatus(currentPoints);
                    i++;
                }
            }
            isRunning.set(false);
        }
    }

    private void bulkUpdateProcessedStatus(List<RawLocationPoint> points) {
        if (points.isEmpty()) {
            return;
        }
        
        log.debug("Bulk updating processed status for {} raw location points", points.size());
        
        String sql = "UPDATE raw_location_points SET processed = true WHERE id = ?";
        
        List<Object[]> batchArgs = points.stream()
                .map(point -> new Object[]{point.getId()})
                .collect(Collectors.toList());
        
        int[] updateCounts = jdbcTemplate.batchUpdate(sql, batchArgs);
        log.debug("Successfully updated processed status for {} raw location points", updateCounts.length);
    }
}
