package com.dedicatedcode.reitti;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.event.TriggerProcessingEvent;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.*;
import com.dedicatedcode.reitti.service.ImportProcessor;
import com.dedicatedcode.reitti.service.ImportStateHolder;
import com.dedicatedcode.reitti.service.UserService;
import com.dedicatedcode.reitti.service.importer.GeoJsonImporter;
import com.dedicatedcode.reitti.service.importer.GpxImporter;
import com.dedicatedcode.reitti.service.processing.LocationDataIngestPipeline;
import com.dedicatedcode.reitti.service.processing.ProcessingPipelineTrigger;
import com.github.sonus21.rqueue.core.RqueueMessageManager;
import com.github.sonus21.rqueue.metrics.RqueueQueueMetrics;
import org.awaitility.Awaitility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TestingService {

    private static final List<String> QUEUES_TO_CHECK = List.of(
            "reitti.place.created.v2"
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
    private RqueueQueueMetrics rqueueQueueMetrics;
    @Autowired
    private RqueueMessageManager messageManager;
    @Autowired
    private TripJdbcService tripRepository;
    @Autowired
    private ProcessedVisitJdbcService processedVisitRepository;
    @Autowired
    private ProcessingPipelineTrigger trigger;
    @Autowired
    private UserService userService;
    @Autowired
    private ImportProcessor importBatchProcessor;
    @Autowired
    private SignificantPlaceJdbcService significantPlaceJdbcService;

    @Autowired
    private LocationDataIngestPipeline locationDataIngestPipeline;

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



    public void processWhileImport(User user, String file) {
        GpxImporter importer = new GpxImporter(new ImportStateHolder(), new ImportProcessor() {
            @Override
            public void processBatch(User user, List<LocationPoint> batch) {
                for (int i = 0; i < batch.size(); i += 5) {
                    int endIndex = Math.min(i + 5, batch.size());
                    List<LocationPoint> chunk = batch.subList(i, endIndex);
                    locationDataIngestPipeline.processLocationData(user.getUsername(), new ArrayList<>(chunk));
                    TriggerProcessingEvent triggerEvent = new TriggerProcessingEvent(user.getUsername(), null, UUID.randomUUID().toString());
                    trigger.handle(triggerEvent, true);
                }
            }

            @Override
            public void scheduleProcessingTrigger(String username) {
            }

            @Override
            public boolean isIdle() {
                return false;
            }
        });
        importer.importGpx(getClass().getResourceAsStream(file), user);
    }

    public User admin() {
        return this.userJdbcService.findById(1L)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + (Long) 1L));
    }

    public User randomUser() {
        return this.userService.createNewUser("test-user_" + UUID.randomUUID(),"Test User", null, null);
    }

    public void awaitDataImport(int seconds) {
        AtomicLong lastRawCount = new AtomicLong(-1);
        AtomicLong lastTripCount = new AtomicLong(-1);
        AtomicInteger stableChecks = new AtomicInteger(0);

        // Require multiple consecutive stable checks
        final int requiredStableChecks = 2;

        Awaitility.await()
                .pollInterval(Math.max(1, seconds / 300), TimeUnit.SECONDS)
                .atMost(seconds, TimeUnit.SECONDS)
                .alias("Wait for processing to complete")
                .until(() -> {
                    // Check all queues are empty
                    boolean queuesAreEmpty = QUEUES_TO_CHECK.stream()
                            .allMatch(name -> {
                                long pendingMessageCount = this.rqueueQueueMetrics.getPendingMessageCount(name);
                                return pendingMessageCount == 0;
                            });

                    if (!queuesAreEmpty) {
                        stableChecks.set(0);
                        return false;
                    }

                    // Check if all counts are stable
                    long currentRawCount = rawLocationPointRepository.count();
                    long currentTripCount = tripRepository.count();

                    boolean countsStable =
                            currentRawCount == lastRawCount.get() &&
                                    currentTripCount == lastTripCount.get();

                    lastRawCount.set(currentRawCount);
                    lastTripCount.set(currentTripCount);

                    if (countsStable && this.trigger.isIdle() && importBatchProcessor.isIdle()) {
                        return stableChecks.incrementAndGet() >= requiredStableChecks;
                    } else {
                        stableChecks.set(0);
                        return false;
                    }
                });
    }

    public void clearData() {
        QUEUES_TO_CHECK.forEach(name -> this.messageManager.deleteAllMessages(name));

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        //now clear the database
        this.tripRepository.deleteAll();
        this.processedVisitRepository.deleteAll();
        this.rawLocationPointRepository.deleteAll();
    }

    public void importAndProcess(User user, String path) {
        importData(user, path);
        awaitDataImport(100);
    }

    public SignificantPlace newSignificantPlace(User user) {
        return this.significantPlaceJdbcService.create(user, SignificantPlace.create(53.48278089848833, 9.32412809124706));
    }
}
