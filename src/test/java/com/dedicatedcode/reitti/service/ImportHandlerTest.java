package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.AbstractIntegrationTest;
import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.RawLocationPointRepository;
import com.dedicatedcode.reitti.repository.UserRepository;
import org.apache.commons.compress.archivers.zip.GeneralPurposeBit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ImportHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private ImportHandler importHandler;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private ImportListener importListener;

    private User testUser;

    @BeforeEach
    void setUp() {

        // Create test user
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setDisplayName("testuser");
        testUser.setPassword(passwordEncoder.encode("password"));
        userRepository.save(testUser);
    }

    @Test
    void shouldImportGPX() {
        InputStream is = getClass().getResourceAsStream("/data/gpx/20250531.gpx");
        Map<String, Object> result = importHandler.importGpx(is, testUser);
        assertEquals(2567, result.get("pointsReceived"));
        assertEquals(true, result.get("success"));

        verify(importListener, times(26)).handle(eq(testUser), ArgumentMatchers.any());
    }
}