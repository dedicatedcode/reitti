package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.AbstractIntegrationTest;
import com.dedicatedcode.reitti.MockImportListener;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ImportHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private ImportHandler importHandler;

    @MockitoBean
    private MockImportListener importListener;

    @Test
    void shouldImportGPX() {
        InputStream is = getClass().getResourceAsStream("/data/gpx/20250531.gpx");
        Map<String, Object> result = importHandler.importGpx(is, user);
        assertEquals(2567, result.get("pointsReceived"));
        assertEquals(true, result.get("success"));

        verify(importListener, times(26)).handleImport(eq(user), ArgumentMatchers.any());
    }
}