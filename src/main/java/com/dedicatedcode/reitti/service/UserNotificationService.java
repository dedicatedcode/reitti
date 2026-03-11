package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.LocationPoint2;
import com.dedicatedcode.reitti.event.SSEEvent;
import com.dedicatedcode.reitti.event.SSEType;
import com.dedicatedcode.reitti.model.NotificationData;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.integration.ReittiSubscriptionService;
import com.github.sonus21.rqueue.core.RqueueMessageEnqueuer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserNotificationService {
    private static final Logger log = LoggerFactory.getLogger(UserNotificationService.class);
    private final RqueueMessageEnqueuer messageEnqueuer;
    private final ReittiSubscriptionService reittiSubscriptionService;

    public UserNotificationService(RqueueMessageEnqueuer messageEnqueuer,
                                 ReittiSubscriptionService reittiSubscriptionService) {
        this.messageEnqueuer = messageEnqueuer;
        this.reittiSubscriptionService = reittiSubscriptionService;
    }

    public void newTrips(User user, List<Trip> trips) {
       newTrips(user, trips, null);
    }

    public void newTrips(User user, List<Trip> trips, String previewId) {
        SSEType eventType = SSEType.TRIPS;
        log.debug("New trips for user [{}]", user.getId());
        Set<LocalDate> dates = calculateAffectedDates(trips.stream().map(Trip::getStartTime).toList(), trips.stream().map(Trip::getEndTime).toList());
        sendToQueue(user, dates, eventType, previewId);
    }

    public void placeUpdate(User user, SignificantPlace place, String previewId) {
        SSEType eventType = SSEType.PLACE;
        log.debug("Place updated for user [{}]", user.getId());
        sendToQueue(user, eventType, previewId);
    }

    public void newVisits(User user, List<ProcessedVisit> processedVisits) {
        SSEType eventType = SSEType.VISITS;
        log.debug("New Visits for user [{}]", user.getId());
        Set<LocalDate> dates = calculateAffectedDates(processedVisits.stream().map(ProcessedVisit::getStartTime).toList(), processedVisits.stream().map(ProcessedVisit::getEndTime).toList());
        sendToQueue(user, dates, eventType, null);
        notifyReittiSubscriptions(user, eventType, dates);
    }

    public void newRawLocationData(User user, List<LocationPoint2> filtered) {
        SSEType eventType = SSEType.RAW_DATA;
        log.debug("New RawLocationPoints for user [{}]", user.getId());
        Set<LocalDate> dates = calculateAffectedDates(filtered.stream().map(LocationPoint2::getTimestamp).toList());
        sendToQueue(user, dates, eventType, null);
        notifyReittiSubscriptions(user, eventType, dates);
    }

    public void sendToQueue(User user, Set<LocalDate> dates, SSEType eventType, String previewId) {
        for (LocalDate date : dates) {
            this.messageEnqueuer.enqueue(MessageDispatcherService.USER_EVENT_QUEUE, new SSEEvent(eventType, user.getId(), user.getId(), date, previewId));
        }
    }

    public void sendToQueue(User user, SSEType eventType, String previewId) {
        this.messageEnqueuer.enqueue(MessageDispatcherService.USER_EVENT_QUEUE, new SSEEvent(eventType, user.getId(), user.getId(), null, previewId));
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
