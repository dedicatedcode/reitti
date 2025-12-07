package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.model.processing.DetectionParameter;
import com.dedicatedcode.reitti.model.processing.RecalculationState;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.DefaultImportProcessor;
import com.dedicatedcode.reitti.service.ImportStateHolder;
import com.dedicatedcode.reitti.service.VisitDetectionParametersService;
import com.dedicatedcode.reitti.service.processing.LocationDataIngestPipeline;
import com.dedicatedcode.reitti.service.processing.ProcessingPipelineTrigger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GoogleIOSTimelineImporterTest {

    @Test
    void shouldParseNewGoogleTakeOutFileFromIOS() {
        LocationDataIngestPipeline mock = mock(LocationDataIngestPipeline.class);
        VisitDetectionParametersService parametersService = mock(VisitDetectionParametersService.class);
        DetectionParameter config = new DetectionParameter(-1L,
                new DetectionParameter.VisitDetection(300, 300),
                new DetectionParameter.VisitMerging(24,300, 100),
                new DetectionParameter.LocationDensity(50, 720),
                null, RecalculationState.DONE);
        when(parametersService.getCurrentConfiguration(any(), any(Instant.class))).thenReturn(config);

        ProcessingPipelineTrigger processingPipeLineTrigger = mock(ProcessingPipelineTrigger.class);
        GoogleIOSTimelineImporter importHandler = new GoogleIOSTimelineImporter(new ObjectMapper(), new ImportStateHolder(), new DefaultImportProcessor(mock, 100, 5, processingPipeLineTrigger));
        User user = new User("test", "Test User");
        Map<String, Object> result = importHandler.importTimeline(getClass().getResourceAsStream("/data/google/timeline_from_ios_randomized.json"), user);

        assertTrue(result.containsKey("success"));
        assertTrue((Boolean) result.get("success"));

        // Create a spy to retrieve all LocationDataEvents pushed into RabbitMQ
        ArgumentCaptor<List<LocationPoint>> eventCaptor = ArgumentCaptor.forClass(List.class);
        verify(mock, times(1)).processLocationData(eq("test"), eventCaptor.capture());

        List<List<LocationPoint>> capturedEvents = eventCaptor.getAllValues();
        assertEquals(1, capturedEvents.size());

        // Verify that all events are for the correct user
        for (List<LocationPoint> points : capturedEvents) {
            assertNotNull(points);
            assertFalse(points.isEmpty());

            points.forEach(point -> assertNotNull(point.getAccuracyMeters()));
        }

    }
}