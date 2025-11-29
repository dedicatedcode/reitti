package com.dedicatedcode.reitti;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.*;
import com.dedicatedcode.reitti.service.UserService;
import com.dedicatedcode.reitti.service.importer.GeoJsonImporter;
import com.dedicatedcode.reitti.service.importer.GpxImporter;
import com.dedicatedcode.reitti.service.processing.ProcessingPipelineTrigger;
import org.awaitility.Awaitility;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TestingService {

    private static final List<String> QUEUES_TO_CHECK = List.of(
            RabbitMQConfig.LOCATION_DATA_QUEUE,
            RabbitMQConfig.SIGNIFICANT_PLACE_QUEUE
    );

    @Autowired
    private UserJdbcService userJdbcService;
    @Autowired
    private GpxImporter gpxImporter;
    @Autowired
    private GeoJsonImporter geoJsonImporter;
    @Autowired
    private RawLocationPointJdbcService rawLocationPointRepository;
    @Autowired
    private RabbitAdmin rabbitAdmin;
    @Autowired
    private TripJdbcService tripRepository;
    @Autowired
    private ProcessedVisitJdbcService processedVisitRepository;
    @Autowired
    private VisitJdbcService visitRepository;
    @Autowired
    private ProcessingPipelineTrigger trigger;
    @Autowired
    private UserService userService;

    public void importData(User user, String path) {
        InputStream is = getClass().getResourceAsStream(path);
        if (path.endsWith(".gpx")) {
            gpxImporter.importGpx(is, user);
        } else if (path.endsWith(".geojson")) {
            geoJsonImporter.importGeoJson(is, user);
        } else {
            throw new IllegalStateException("Unsupported file type: " + path);
        }
    }

    public User admin() {
        return this.userJdbcService.findById(1L)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + (Long) 1L));
    }

    public User randomUser() {
        return this.userService.createNewUser("test-user_" + UUID.randomUUID().toString(),"Test User", null, null);
    }

    public void triggerProcessingPipeline(int timeout) {
        trigger.start();
        awaitDataImport(timeout);
    }
    public void awaitDataImport(int seconds) {
        AtomicLong lastRawCount = new AtomicLong(-1);
        AtomicLong lastVisitCount = new AtomicLong(-1);
        AtomicLong lastTripCount = new AtomicLong(-1);
        AtomicInteger stableChecks = new AtomicInteger(0);

        // Require multiple consecutive stable checks
        final int requiredStableChecks = 5;

        Awaitility.await()
                .pollInterval(Math.max(1, seconds / 300), TimeUnit.SECONDS)
                .atMost(seconds, TimeUnit.SECONDS)
                .alias("Wait for processing to complete")
                .until(() -> {
                    // Check all queues are empty
                    boolean queuesAreEmpty = QUEUES_TO_CHECK.stream()
                            .allMatch(name -> {
                                var queueInfo = this.rabbitAdmin.getQueueInfo(name);
                                return queueInfo.getMessageCount() == 0;
                            });

                    if (!queuesAreEmpty) {
                        stableChecks.set(0);
                        return false;
                    }

                    // Check if all counts are stable
                    long currentRawCount = rawLocationPointRepository.count();
                    long currentVisitCount = visitRepository.count();
                    long currentTripCount = tripRepository.count();

                    boolean countsStable =
                            currentRawCount == lastRawCount.get() &&
                                    currentVisitCount == lastVisitCount.get() &&
                                    currentTripCount == lastTripCount.get();

                    lastRawCount.set(currentRawCount);
                    lastVisitCount.set(currentVisitCount);
                    lastTripCount.set(currentTripCount);

                    if (countsStable && this.trigger.isIdle()) {
                        return stableChecks.incrementAndGet() >= requiredStableChecks;
                    } else {
                        stableChecks.set(0);
                        return false;
                    }
                });
    }

    public void clearData() {
        QUEUES_TO_CHECK.forEach(name -> this.rabbitAdmin.purgeQueue(name));

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        //now clear the database
        this.tripRepository.deleteAll();
        this.processedVisitRepository.deleteAll();
        this.visitRepository.deleteAll();
        this.rawLocationPointRepository.deleteAll();
    }

    public void importAndProcess(User user, String path) {
        importData(user, path);
        awaitDataImport(100);
    }
}
