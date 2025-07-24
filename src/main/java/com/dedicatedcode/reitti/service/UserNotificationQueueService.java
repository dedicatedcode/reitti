package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.event.SSEEvent;
import com.dedicatedcode.reitti.event.SSEType;
import com.dedicatedcode.reitti.model.ProcessedVisit;
import com.dedicatedcode.reitti.model.Trip;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserNotificationQueueService {
    private static final Logger log = LoggerFactory.getLogger(UserNotificationQueueService.class);
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final RabbitTemplate rabbitTemplate;

    public UserNotificationQueueService(UserSettingsJdbcService userSettingsJdbcService,
                                        RabbitTemplate rabbitTemplate) {
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.rabbitTemplate = rabbitTemplate;
    }

    public void newTrips(User user, List<Trip> trips) {
        SSEType eventType = SSEType.TRIPS;
        Set<Long> parentUserIds = new HashSet<>(this.userSettingsJdbcService.findParentUserIds(user));
        log.debug("New trips for user [{}], will be notify [{}] number or parent users", user.getId(), parentUserIds.size());
        Set<LocalDate> dates = calculateAffectedDates(trips.stream().map(Trip::getStartTime).toList(), trips.stream().map(Trip::getEndTime).toList());
        sendToQueue(user, dates, parentUserIds, eventType);
    }

    public void newVisits(User user, List<ProcessedVisit> processedVisits) {
        SSEType eventType = SSEType.VISITS;
        Set<Long> parentUserIds = new HashSet<>(this.userSettingsJdbcService.findParentUserIds(user));
        log.debug("New Visits for user [{}], will be notify [{}] number or parent users", user.getId(), parentUserIds.size());
        Set<LocalDate> dates = calculateAffectedDates(processedVisits.stream().map(ProcessedVisit::getStartTime).toList(), processedVisits.stream().map(ProcessedVisit::getEndTime).toList());
        sendToQueue(user, dates, parentUserIds, eventType);
    }

    public void newRawLocationData(User user, List<LocationDataRequest.LocationPoint> filtered) {
        SSEType eventType = SSEType.RAW_DATA;
        Set<Long> parentUserIds = new HashSet<>(this.userSettingsJdbcService.findParentUserIds(user));
        log.debug("New RawLocationPoints for user [{}], will be notify [{}] number or parent users", user.getId(), parentUserIds.size());
        Set<LocalDate> dates = calculateAffectedDates(filtered.stream().map(LocationDataRequest.LocationPoint::getTimestamp).map(s -> ZonedDateTime.parse(s).toInstant()).toList());
        sendToQueue(user, dates, parentUserIds, eventType);
    }

    private void sendToQueue(User user, Set<LocalDate> dates, Set<Long> parentUserIds, SSEType eventType) {
        for (LocalDate date : dates) {
            for (Long parentUserId : parentUserIds) {
                this.rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.USER_EVENT_ROUTING_KEY, new SSEEvent(eventType, parentUserId, user.getId(), date));
            }
            this.rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.USER_EVENT_ROUTING_KEY, new SSEEvent(eventType, user.getId(), user.getId(), date));
        }
    }

    @SafeVarargs
    private Set<LocalDate> calculateAffectedDates(List<Instant>... list) {
        if (list == null) {
            return new HashSet<>();
        } else {
            Set<LocalDate> result = new HashSet<>();
            for (List<Instant> instants : list) {
                result.addAll(instants.stream().map(instant -> instant.atZone(ZoneId.of("Z")).toLocalDate()).collect(Collectors.toSet()));
            }
            return result;
        }
    }

}
