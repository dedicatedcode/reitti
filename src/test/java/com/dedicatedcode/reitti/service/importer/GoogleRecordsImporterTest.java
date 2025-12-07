package com.dedicatedcode.reitti.service.importer;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GoogleRecordsImporterTest {

    @Test
    void shouldParseOldFormat() {
        RabbitTemplate mock = mock(RabbitTemplate.class);
//        GoogleRecordsImporter importHandler = new GoogleRecordsImporter(new ObjectMapper(), new ImportStateHolder(), new ImportBatchProcessor(mock, 100, 5));
//        User user = new User("test", "Test User");
//        Map<String, Object> result = importHandler.importGoogleRecords(getClass().getResourceAsStream("/data/google/Records.json"), user);
//
//        assertTrue(result.containsKey("success"));
//        assertTrue((Boolean) result.get("success"));
//        verify(mock, times(1)).convertAndSend(eq(RabbitMQConfig.EXCHANGE_NAME), eq(RabbitMQConfig.LOCATION_DATA_ROUTING_KEY), any(LocationDataEvent.class));
    }
}