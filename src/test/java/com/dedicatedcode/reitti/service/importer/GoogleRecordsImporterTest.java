package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.service.ImportStateHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GoogleRecordsImporterTest {

    @Test
    void shouldParseOldFormat() {
        RabbitTemplate mock = mock(RabbitTemplate.class);
        GoogleRecordsImporter importHandler = new GoogleRecordsImporter(new ObjectMapper(), new ImportStateHolder(), new ImportBatchProcessor(mock, 100));
        User user = new User("test", "Test User");
        Map<String, Object> result = importHandler.importGoogleRecords(getClass().getResourceAsStream("/data/google/Records.json"), user);

        assertTrue(result.containsKey("success"));
        assertTrue((Boolean) result.get("success"));
        verify(mock, times(1)).convertAndSend(eq(RabbitMQConfig.EXCHANGE_NAME), eq(RabbitMQConfig.LOCATION_DATA_ROUTING_KEY), any(LocationDataEvent.class));
    }
}