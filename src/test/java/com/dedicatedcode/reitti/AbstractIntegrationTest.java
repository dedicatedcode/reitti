package com.dedicatedcode.reitti;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.event.MergeVisitEvent;
import com.dedicatedcode.reitti.model.RawLocationPoint;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.*;
import com.dedicatedcode.reitti.service.ImportHandler;
import com.dedicatedcode.reitti.service.ImportListener;
import com.dedicatedcode.reitti.service.LocationDataService;
import com.dedicatedcode.reitti.service.processing.StayPoint;
import com.dedicatedcode.reitti.service.processing.StayPointDetectionService;
import com.dedicatedcode.reitti.service.processing.VisitMergingService;
import com.dedicatedcode.reitti.service.processing.VisitService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
import java.util.List;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext
@Import(AbstractIntegrationTest.TestConfig.class)
public abstract class AbstractIntegrationTest {

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

    protected List<RawLocationPoint> importGpx(String filename) {
        InputStream is = getClass().getResourceAsStream(filename);
        importHandler.importGpx(is, user);
        List<LocationDataRequest.LocationPoint> allPoints = this.importListener.getPoints();
        return locationDataService.processLocationData(user, allPoints);
    }

    protected void importUntilVisits(String fileName) {
        List<RawLocationPoint> savedPoints = importGpx(fileName);
        List<StayPoint> stayPoints = stayPointDetectionService.detectStayPoints(user, savedPoints);

        if (!stayPoints.isEmpty()) {
            visitService.processStayPoints(user, stayPoints);
        }
    }

    protected void importUntilProcessedVisits(String fileName) {
        importUntilVisits(fileName);
        visitMergingService.mergeVisits(new MergeVisitEvent(user.getUsername(), null, null));
    }
}
