package com.dedicatedcode.reitti.controller.api.ingestion;

import com.dedicatedcode.reitti.TestContainersConfig;
import com.dedicatedcode.reitti.TestSecurityConfig;
import com.dedicatedcode.reitti.dto.OwntracksFriendResponse;
import com.dedicatedcode.reitti.dto.OwntracksLocationRequest;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSharing;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.UserSharingJdbcService;
import com.dedicatedcode.reitti.service.AvatarService;
import com.dedicatedcode.reitti.service.LocationBatchingService;
import com.dedicatedcode.reitti.service.integration.ReittiIntegrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestContainersConfig.class, TestSecurityConfig.class})
@Transactional
class OwntracksIngestApiControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserJdbcService userJdbcService;

    @Autowired
    private UserSharingJdbcService userSharingJdbcService;

    @Autowired
    private RawLocationPointJdbcService rawLocationPointJdbcService;

    @Autowired
    private AvatarService avatarService;

    @MockBean
    private LocationBatchingService locationBatchingService;

    @MockBean
    private ReittiIntegrationService reittiIntegrationService;

    private User testUser;
    private User sharedUser;

    @BeforeEach
    void setUp() {
        // Create test users
        testUser = userJdbcService.createUser("testuser", "Test User", "password", null, null);
        sharedUser = userJdbcService.createUser("shareduser", "Shared User", "password", null, null);

        // Create sharing relationship
        userSharingJdbcService.createSharing(sharedUser.getId(), testUser.getId(), "#FF0000");

        // Add some location data for shared user
        rawLocationPointJdbcService.save(new RawLocationPoint(
                null,
                sharedUser.getId(),
                60.1699,
                24.9384,
                10.0,
                50.0,
                100.0,
                100.0,
                100.0,
                100.0,
                100.0,
                100.0,
                100.0,
                100.0,
                100.0,
                100.0,
                100.0,
                100.0,
                100.0,
                100.0,
                100.0,
                100.0,
                100.0,
                100.0,
                100.0,
                100.0,
                100.0,
                100.0,
                100.0,
                100.0,
                100.0,
                100.0,
                100.0,
                100.0,
                100.0,
                100.0,
                100.0,