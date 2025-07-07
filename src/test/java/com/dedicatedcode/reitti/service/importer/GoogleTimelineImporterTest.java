package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GoogleTimelineImporterTest {


    @Test
    void shouldParseNewGoogleTakeOutFile() {
        RabbitTemplate mock = mock(RabbitTemplate.class);
        GoogleTimelineImporter importHandler = new GoogleTimelineImporter(new ObjectMapper(), new ImportBatchProcessor(mock, 100));
        User user = new User("test", "Test User");
        Map<String, Object> result = importHandler.importGoogleTimeline(getClass().getResourceAsStream("/data/google/Zeitachse.json"), user);

        assertTrue(result.containsKey("success"));
        assertTrue((Boolean) result.get("success"));
        verify(mock, times(3)).convertAndSend(eq(RabbitMQConfig.EXCHANGE_NAME), eq(RabbitMQConfig.LOCATION_DATA_ROUTING_KEY), any(LocationDataEvent.class));
    }
}