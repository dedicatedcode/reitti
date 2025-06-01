package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.AbstractIntegrationTest;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.model.Visit;
import com.dedicatedcode.reitti.repository.ProcessedVisitRepository;
import com.dedicatedcode.reitti.repository.UserRepository;
import com.dedicatedcode.reitti.repository.VisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisitMergingServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private VisitRepository visitRepository;

    @Autowired
    private ProcessedVisitRepository processedVisitRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Clean up repositories
        processedVisitRepository.deleteAll();
        visitRepository.deleteAll();
        userRepository.deleteAll();

        // Create test user
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setPassword(passwordEncoder.encode("password"));
        testUser = userRepository.save(testUser);
    }

    @Test
    void testContainersAreRunning() {
        // This test just verifies that our containers are running and Spring context loads
        assertTrue(true, "Containers should be running");
    }

    @Test
    void testCreateAndSaveVisit() {
        // Create a test visit
        Visit visit = new Visit();
        visit.setUser(testUser);
        visit.setLatitude(60.1699);
        visit.setLongitude(24.9384);
        visit.setStartTime(Instant.now().minusSeconds(3600));
        visit.setEndTime(Instant.now());
        visit.setProcessed(false);
        
        // Save the visit
        Visit savedVisit = visitRepository.save(visit);
        
        // Verify it was saved
        List<Visit> visits = visitRepository.findByUser(testUser);
        assertEquals(1, visits.size());
        assertEquals(savedVisit.getId(), visits.get(0).getId());
    }
}
