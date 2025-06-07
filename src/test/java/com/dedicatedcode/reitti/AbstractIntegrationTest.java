package com.dedicatedcode.reitti;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.event.MergeVisitEvent;
import com.dedicatedcode.reitti.model.*;
import com.dedicatedcode.reitti.repository.*;
import com.dedicatedcode.reitti.service.ImportHandler;
import com.dedicatedcode.reitti.service.LocationDataService;
import com.dedicatedcode.reitti.service.processing.*;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext
@Import(AbstractIntegrationTest.TestConfig.class)
public abstract class AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(AbstractIntegrationTest.class);

    @Container
    static PostgreSQLContainer<?> timescaledb = new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:17-3.5-alpine")
            .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("reitti")
            .withUsername("test")
            .withPassword("test");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3-management")
            .withExposedPorts(5672, 15672);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        // Database properties
        registry.add("spring.datasource.url", timescaledb::getJdbcUrl);
        registry.add("spring.datasource.username", timescaledb::getUsername);
        registry.add("spring.datasource.password", timescaledb::getPassword);

        // RabbitMQ properties
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);
    }

    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected VisitRepository visitRepository;
    @Autowired
    protected ProcessedVisitRepository processedVisitRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected RawLocationPointRepository rawLocationPointRepository;
    @Autowired
    protected SignificantPlaceRepository significantPlaceRepository;

    @Autowired
    protected MockImportListener importListener;

    @Autowired
    protected TripRepository tripsRepository;

    @Autowired
    private LocationDataService locationDataService;

    @Autowired
    private StayPointDetectionService stayPointDetectionService;

    @Autowired
    private VisitService visitService;

    @Autowired
    private ImportHandler importHandler;

    @Autowired
    private VisitMergingService visitMergingService;

    @Autowired
    private TripDetectionService tripDetectionService;

    @Autowired
    private TripMergingService tripMergingService;

    protected User user;

    @BeforeEach
    void setUp() {
        // Clean up repositories
        tripsRepository.deleteAll();
        significantPlaceRepository.deleteAll();
        processedVisitRepository.deleteAll();
        visitRepository.deleteAll();
        rawLocationPointRepository.deleteAll();
        userRepository.deleteAll();
        importListener.clearAll();

        // Create test user
        user = new User();
        user.setUsername("testuser");
        user.setDisplayName("testuser");
        user.setPassword(passwordEncoder.encode("password"));
        user = userRepository.save(user);
    }

    @TestConfiguration
    public static class TestConfig {
        @Bean(name = "importListener")
        public MockImportListener importListener() {
            return new MockImportListener();
        }
    }

    @SuppressWarnings("unchecked")
    protected List<RawLocationPoint> importGpx(String filename) {
        return (List<RawLocationPoint>) importData(filename, ImportStep.RAW_POINTS);
    }

    @SuppressWarnings("unchecked")
    protected List<StayPoint> importUntilStayPoints(String filename) {
        return (List<StayPoint>) importData(filename, ImportStep.STAY_POINTS);
    }

    @SuppressWarnings("unchecked")
    protected List<Visit> importUntilVisits(String fileName) {
        return (List<Visit>) importData(fileName, ImportStep.VISITS);
    }

    @SuppressWarnings("unchecked")
    protected List<ProcessedVisit> importUntilProcessedVisits(String fileName) {
        return (List<ProcessedVisit>) importData(fileName, ImportStep.MERGE_VISITS);
    }

    @SuppressWarnings("unchecked")
    protected List<Trip> importUntilTrips(String fileName) {
        return (List<Trip>) importData(fileName, ImportStep.TRIPS);
    }

    @SuppressWarnings("unchecked")
    protected List<Trip> importUntilMergedTrips(String fileName) {
        return (List<Trip>) importData(fileName, ImportStep.MERGE_TRIPS);
    }

    private List<?> importData(String fileName, ImportStep untilStep) {
        InputStream is = getClass().getResourceAsStream(fileName);
        importHandler.importGpx(is, user);
        List<LocationDataRequest.LocationPoint> allPoints = this.importListener.getPoints();
        List<RawLocationPoint> savedPoints = locationDataService.processLocationData(user, allPoints);

        log.info("Imported [{}] raw location points", savedPoints.size());
        if (untilStep == ImportStep.RAW_POINTS) {
            return savedPoints;
        }

        int splitSize = 100;
        List<StayPoint> stayPoints = new ArrayList<>();
        while (savedPoints.size() >= splitSize) {
            List<RawLocationPoint> current = new ArrayList<>(savedPoints.subList(0, splitSize));
            savedPoints.removeAll(current);
            stayPoints.addAll(stayPointDetectionService.detectStayPoints(user, current));
        }
        if (!savedPoints.isEmpty()) {
            stayPoints.addAll(stayPointDetectionService.detectStayPoints(user, savedPoints));
        }
        log.info("Created [{}] stay points", stayPoints.size());
        if (untilStep == ImportStep.STAY_POINTS) {
            return savedPoints;
        }

        visitService.processStayPoints(user, stayPoints);
        log.info("Created [{}] visits out of [{}] stay points", this.visitRepository.count(), stayPoints.size());
        if (untilStep == ImportStep.VISITS) {
            return this.visitRepository.findAll();
        }

        MergeVisitEvent visitEvent = new MergeVisitEvent(user.getUsername(), null, null);

        visitMergingService.mergeVisits(visitEvent);
        log.info("Merged [{}] visits into [{}] processed visits", this.visitRepository.count(), this.processedVisitRepository.count());
        if (untilStep == ImportStep.MERGE_VISITS) {
            return this.processedVisitRepository.findAll();
        }

        tripDetectionService.detectTripsForUser(visitEvent);
        long processedTripsCount = this.processedVisitRepository.count();
        log.info("Found [{}] trips between [{}] processed visits", this.tripsRepository.count(), processedTripsCount);
        if (untilStep == ImportStep.TRIPS) {
            return this.tripsRepository.findAll();
        }

        tripMergingService.mergeDuplicateTripsForUser(visitEvent);
        log.info("Merged [{}] processed trips into [{}] processed visits", processedTripsCount, this.processedVisitRepository.count());
        return this.tripsRepository.findAll();
    }

    public enum ImportStep {
        RAW_POINTS,
        STAY_POINTS,
        VISITS,
        MERGE_VISITS,
        TRIPS,
        MERGE_TRIPS;

    }
}
