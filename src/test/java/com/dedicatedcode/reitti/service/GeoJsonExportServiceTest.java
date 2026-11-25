package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.SourceLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.io.StringWriter;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeoJsonExportServiceTest {

    @Mock
    private SourceLocationPointJdbcService sourceLocationPointJdbcService;
    @Mock
    private RawLocationPointJdbcService rawLocationPointJdbcService;
    @Mock
    private User user;
    @Mock
    private Device device;

    private GeoJsonExportService candidate;

    private final Instant start = Instant.parse("2026-11-23T00:00:00Z");
    private final Instant end = Instant.parse("2026-11-24T00:00:00Z");

    @BeforeEach
    void setUp() {
        this.candidate = new GeoJsonExportService(sourceLocationPointJdbcService, rawLocationPointJdbcService, new ObjectMapper());
    }

    @Test
    void regularExportKeepsIgnoredAndInvalidPoints() throws Exception {
        SourceLocationPoint point = new SourceLocationPoint(1L, Instant.parse("2026-11-23T18:45:14Z"),
                new GeoPoint(50.7666626, 4.3088291), 10.0, 100.0);
        when(device.id()).thenReturn(2L);
        when(sourceLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(
                user, device, start, end, true, true)).thenReturn(List.of(point));

        StringWriter writer = new StringWriter();
        candidate.generateGeoJsonContentStreaming(user, start, end, device, writer);

        verify(sourceLocationPointJdbcService).findByUserAndTimestampBetweenOrderByTimestampAsc(
                user, device, start, end, true, true);
        assertTrue(writer.toString().contains("\"id\":1"));
    }

    @Test
    void workbenchSourceExcludesUserIgnoredPoints() throws Exception {
        SourceLocationPoint point = new SourceLocationPoint(2L, Instant.parse("2026-11-23T18:45:15Z"),
                new GeoPoint(50.7652579, 4.2859756), 10.0, 100.0);
        when(device.id()).thenReturn(2L);
        when(sourceLocationPointJdbcService.findWorkbenchPoints(user, device, start, end)).thenReturn(List.of(point));

        StringWriter writer = new StringWriter();
        candidate.generateWorkbenchSourceGeoJsonContentStreaming(user, start, end, device, writer);

        verify(sourceLocationPointJdbcService).findWorkbenchPoints(user, device, start, end);
        assertTrue(writer.toString().contains("\"id\":2"));
    }
}
