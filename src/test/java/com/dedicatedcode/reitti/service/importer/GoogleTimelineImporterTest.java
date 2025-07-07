package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GoogleTimelineImporterTest {


    @Test
    void shouldParseNewGoogleTakeOutFile() {
        RabbitTemplate mock = mock(RabbitTemplate.class);
        GoogleTimelineImporter importHandler = new GoogleTimelineImporter(new ObjectMapper(), new ImportBatchProcessor(mock, 100), 5, 100);
        User user = new User("test", "Test User");
        Map<String, Object> result = importHandler.importGoogleTimeline(getClass().getResourceAsStream("/data/google/tl_randomized.json"), user);

        assertTrue(result.containsKey("success"));
        assertTrue((Boolean) result.get("success"));

        // Create a spy to retrieve all LocationDataEvents pushed into RabbitMQ
        ArgumentCaptor<LocationDataEvent> eventCaptor = ArgumentCaptor.forClass(LocationDataEvent.class);
        verify(mock, times(5)).convertAndSend(eq(RabbitMQConfig.EXCHANGE_NAME), eq(RabbitMQConfig.LOCATION_DATA_ROUTING_KEY), eventCaptor.capture());
        
        List<LocationDataEvent> capturedEvents = eventCaptor.getAllValues();
        assertEquals(5, capturedEvents.size());
        
        // Verify that all events are for the correct user
        for (LocationDataEvent event : capturedEvents) {
            assertEquals("test", event.getUsername());
            assertNotNull(event.getPoints());
            assertFalse(event.getPoints().isEmpty());

            event.getPoints().forEach(point -> assertNotNull(point.getAccuracyMeters()));
        }
    }
}
