package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.event.SSEEvent;
import com.dedicatedcode.reitti.event.SSEType;
import com.dedicatedcode.reitti.model.NotificationData;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSharing;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.UserSharingJdbcService;
import com.dedicatedcode.reitti.service.integration.ReittiSubscriptionService;
import org.jobrunr.scheduling.JobScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserNotificationService {
    private static final Logger log = LoggerFactory.getLogger(UserNotificationService.class);
    private final ReittiSubscriptionService reittiSubscriptionService;
    private final UserJdbcService userJdbcService;
    private final UserSharingJdbcService userSharingJdbcService;
    private final JobScheduler jobScheduler;
    private final UserSseEmitterService userSseEmitterService;

    public UserNotificationService(JobScheduler jobScheduler,
                                   ReittiSubscriptionService reittiSubscriptionService,
                                   UserJdbcService userJdbcService,
                                   UserSharingJdbcService userSharingJdbcService, UserSseEmitterService userSseEmitterService) {
        this.jobScheduler = jobScheduler;
        this.reittiSubscriptionService = reittiSubscriptionService;
        this.userJdbcService = userJdbcService;
        this.userSharingJdbcService = userSharingJdbcService;
        this.userSseEmitterService = userSseEmitterService;
    }

    public void newTrips(User user, List<Trip> trips) {
       newTrips(user, trips, null);
    }

    public void newTrips(User user, List<Trip> trips, String previewId) {
        SSEType eventType = SSEType.TRIPS;
        log.debug("New trips for user [{}]", user.getId());
        Set<LocalDate> dates = calculateAffectedDates(trips.stream().map(Trip::getStartTime).toList(), trips.stream().map(Trip::getEndTime).toList());
        sendToQueue(user, dates, eventType, previewId);
        notifyOtherUsers(user, eventType, dates);
        notifyReittiSubscriptions(user, eventType, dates);
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
        notifyOtherUsers(user, eventType, dates);
        notifyReittiSubscriptions(user, eventType, dates);
    }

    public void newRawLocationData(User user, List<LocationPoint> filtered) {
        SSEType eventType = SSEType.RAW_DATA;
        log.debug("New RawLocationPoints for user [{}]", user.getId());
        Set<LocalDate> dates = calculateAffectedDates(filtered.stream().map(LocationPoint::getTimestamp).toList());
        sendToQueue(user, dates, eventType, null);
        notifyOtherUsers(user, eventType, dates);
        notifyReittiSubscriptions(user, eventType, dates);
    }

    public void sendToQueue(User user, Set<LocalDate> dates, SSEType eventType, String previewId) {
        for (LocalDate date : dates) {
            this.jobScheduler.enqueue(() -> {
                    this.userSseEmitterService.sendEventToUser(user, new SSEEvent(eventType, user.getId(), user.getId(), date, previewId));
            });
        }
    }
    public void sendToQueue(User user, User changedUser, Set<LocalDate> dates, SSEType eventType, String previewId) {
        for (LocalDate date : dates) {
            this.jobScheduler.enqueue(() -> {
                this.userSseEmitterService.sendEventToUser(user, new SSEEvent(eventType, user.getId(), changedUser.getId(), date, previewId));
            });
        }
    }

    private void sendToQueue(User user, SSEType eventType, String previewId) {
        this.jobScheduler.enqueue(() -> {
            this.userSseEmitterService.sendEventToUser(user, new SSEEvent(eventType, user.getId(), user.getId(), null, previewId));
        });
    }

    private void notifyOtherUsers(User user, SSEType eventType, Set<LocalDate> dates) {
        calculatedAffectedUsers(user).forEach(otherUser -> sendToQueue(otherUser, user, dates, eventType, null));
    }

    private void notifyReittiSubscriptions(User user, SSEType eventType, Set<LocalDate> dates) {
        try {
            NotificationData notificationData = new NotificationData(eventType, user.getId(), dates);
            reittiSubscriptionService.notifyAllSubscriptions(user, notificationData);
        } catch (Exception e) {
            log.error("Failed to notify Reitti subscriptions for user: {}", user.getId(), e);
        }
    }

    private Set<User> calculatedAffectedUsers(User user) {
        return this.userSharingJdbcService.findBySharingUser(user.getId()).stream()
                .map(UserSharing::getSharedWithUserId)
                .map(userJdbcService::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
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
