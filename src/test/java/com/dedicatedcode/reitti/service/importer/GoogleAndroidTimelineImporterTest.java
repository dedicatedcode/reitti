package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.service.ImportStateHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GoogleAndroidTimelineImporterTest {

    @Test
    void shouldParseNewGoogleTakeOutFileFromAndroid() {
        RabbitTemplate mock = mock(RabbitTemplate.class);
        GoogleAndroidTimelineImporter importHandler = new GoogleAndroidTimelineImporter(new ObjectMapper(), new ImportStateHolder(), new ImportBatchProcessor(mock, 100, 15), 5, 100, 300);
        User user = new User("test", "Test User");
        Map<String, Object> result = importHandler.importTimeline(getClass().getResourceAsStream("/data/google/timeline_from_android_randomized.json"), user);

        assertTrue(result.containsKey("success"));
        assertTrue((Boolean) result.get("success"));

        // Create a spy to retrieve all LocationDataEvents pushed into RabbitMQ
        ArgumentCaptor<LocationDataEvent> eventCaptor = ArgumentCaptor.forClass(LocationDataEvent.class);
        verify(mock, times(30)).convertAndSend(eq(RabbitMQConfig.EXCHANGE_NAME), eq(RabbitMQConfig.LOCATION_DATA_ROUTING_KEY), eventCaptor.capture());

        List<LocationDataEvent> capturedEvents = eventCaptor.getAllValues();
        assertEquals(30, capturedEvents.size());

        // Verify that all events are for the correct user
        for (LocationDataEvent event : capturedEvents) {
            assertEquals("test", event.getUsername());
            assertNotNull(event.getPoints());
            assertFalse(event.getPoints().isEmpty());

            event.getPoints().forEach(point -> assertNotNull(point.getAccuracyMeters()));
        }
    }
}
