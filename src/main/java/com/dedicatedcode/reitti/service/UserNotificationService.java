package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.event.SSEEvent;
import com.dedicatedcode.reitti.event.SSEType;
import com.dedicatedcode.reitti.model.NotificationData;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.integration.ReittiSubscriptionService;
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
public class UserNotificationService {
    private static final Logger log = LoggerFactory.getLogger(UserNotificationService.class);
    private final RabbitTemplate rabbitTemplate;
    private final ReittiSubscriptionService reittiSubscriptionService;

    public UserNotificationService(RabbitTemplate rabbitTemplate, 
                                 ReittiSubscriptionService reittiSubscriptionService) {
        this.rabbitTemplate = rabbitTemplate;
        this.reittiSubscriptionService = reittiSubscriptionService;
    }

    public void newTrips(User user, List<Trip> trips) {
        SSEType eventType = SSEType.TRIPS;
        log.debug("New trips for user [{}]", user.getId());
        Set<LocalDate> dates = calculateAffectedDates(trips.stream().map(Trip::getStartTime).toList(), trips.stream().map(Trip::getEndTime).toList());
        sendToQueue(user, dates, eventType);
        notifyReittiSubscriptions(user, eventType, dates);
    }

    public void newVisits(User user, List<ProcessedVisit> processedVisits) {
        SSEType eventType = SSEType.VISITS;
        log.debug("New Visits for user [{}]", user.getId());
        Set<LocalDate> dates = calculateAffectedDates(processedVisits.stream().map(ProcessedVisit::getStartTime).toList(), processedVisits.stream().map(ProcessedVisit::getEndTime).toList());
        sendToQueue(user, dates, eventType);
        notifyReittiSubscriptions(user, eventType, dates);
    }

    public void newRawLocationData(User user, List<LocationDataRequest.LocationPoint> filtered) {
        SSEType eventType = SSEType.RAW_DATA;
        log.debug("New RawLocationPoints for user [{}]", user.getId());
        Set<LocalDate> dates = calculateAffectedDates(filtered.stream().map(LocationDataRequest.LocationPoint::getTimestamp).map(s -> ZonedDateTime.parse(s).toInstant()).toList());
        sendToQueue(user, dates, eventType);
        notifyReittiSubscriptions(user, eventType, dates);
    }

    public void sendToQueue(User user, Set<LocalDate> dates, SSEType eventType) {
        for (LocalDate date : dates) {
            this.rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.USER_EVENT_ROUTING_KEY, new SSEEvent(eventType, user.getId(), user.getId(), date));
        }
    }

    private void notifyReittiSubscriptions(User user, SSEType eventType, Set<LocalDate> dates) {
        try {
            NotificationData notificationData = new NotificationData(eventType, user.getId(), dates);
            reittiSubscriptionService.notifyAllSubscriptions(user, notificationData);
        } catch (Exception e) {
            log.error("Failed to notify Reitti subscriptions for user: {}", user.getId(), e);
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
