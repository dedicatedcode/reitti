package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.event.SSEEvent;
import com.dedicatedcode.reitti.event.SSEType;
import com.dedicatedcode.reitti.model.security.User;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@IntegrationTest
class UserNotificationServiceTest {
    @Autowired
    private UserNotificationService userNotificationService;

    @MockitoSpyBean
    private UserSseEmitterService userSseEmitterService;

    @Autowired
    private TestingService testingService;

    private User user;

    @BeforeEach
    void setUp() {
        this.user = testingService.randomUser();
        reset(userSseEmitterService);
    }

    @Test
    void shouldEnqueueAndDeliverRawDataEvent() {
        LocationPoint point = new LocationPoint();
        point.setLatitude(53.80837);
        point.setLongitude(10.37092);
        point.setTimestamp(Instant.parse("2025-06-18T08:00:00Z"));
        point.setAccuracyMeters(5.0);

        userNotificationService.newRawLocationData(user, List.of(point));

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(userSseEmitterService, atLeastOnce()).sendEventToUser(eq(user), argThat(event ->
                    event.getType() == SSEType.RAW_DATA
                            && user.getId().equals(event.getUserId())
                            && user.getId().equals(event.getChangedUserId())
                            && event.getPreviewId() == null
            ));
        });
    }

    @Test
    void shouldEnqueueAndDeliverPlaceUpdateEvent() {
        userNotificationService.placeUpdate(user, null, "preview-123");

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(userSseEmitterService, atLeastOnce()).sendEventToUser(eq(user), argThat(event ->
                    event.getType() == SSEType.PLACE
                            && "preview-123".equals(event.getPreviewId())
            ));
        });
    }

    @Test
    void shouldEnqueueAndDeliverMultipleDateEvents() {
        LocationPoint point1 = new LocationPoint();
        point1.setLatitude(50.0);
        point1.setLongitude(10.0);
        point1.setTimestamp(Instant.parse("2025-06-18T08:00:00Z"));
        point1.setAccuracyMeters(5.0);

        LocationPoint point2 = new LocationPoint();
        point2.setLatitude(51.0);
        point2.setLongitude(11.0);
        point2.setTimestamp(Instant.parse("2025-06-19T12:00:00Z"));
        point2.setAccuracyMeters(5.0);

        userNotificationService.newRawLocationData(user, List.of(point1, point2));

        ArgumentCaptor<SSEEvent> eventCaptor = ArgumentCaptor.forClass(SSEEvent.class);
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(userSseEmitterService, atLeast(2)).sendEventToUser(eq(user), eventCaptor.capture());
            List<SSEEvent> events = eventCaptor.getAllValues();
            assertEquals(2, events.size());
            assertTrue(events.stream().anyMatch(e -> "2025-06-18".equals(e.getDate().toString())));
            assertTrue(events.stream().anyMatch(e -> "2025-06-19".equals(e.getDate().toString())));
        });
    }
}
