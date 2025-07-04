package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;


class ImportHandlerTest {

    @Test
    void shouldParseNewGoogleTakeOutFile() {
        RabbitTemplate mock = mock(RabbitTemplate.class);
        ImportHandler importHandler = new ImportHandler(new ObjectMapper(), mock, 100);
        User user = new User("test", "Test User");
        Map<String, Object> result = importHandler.importGoogleTakeout(getClass().getResourceAsStream("/data/google/Zeitachse.json"), user);

        assertTrue(result.containsKey("success"));
        assertTrue((Boolean) result.get("success"));
        verify(mock, times(3)).convertAndSend(eq(RabbitMQConfig.EXCHANGE_NAME), eq(RabbitMQConfig.LOCATION_DATA_ROUTING_KEY), any(LocationDataEvent.class));
    }

    @Test
    void shouldParseOldFormat() {
        RabbitTemplate mock = mock(RabbitTemplate.class);
        ImportHandler importHandler = new ImportHandler(new ObjectMapper(), mock, 100);
        User user = new User("test", "Test User");
        Map<String, Object> result = importHandler.importGoogleTakeout(getClass().getResourceAsStream("/data/google/Records.json"), user);

        assertTrue(result.containsKey("success"));
        assertTrue((Boolean) result.get("success"));
        verify(mock, times(1)).convertAndSend(eq(RabbitMQConfig.EXCHANGE_NAME), eq(RabbitMQConfig.LOCATION_DATA_ROUTING_KEY), any(LocationDataEvent.class));
    }
}