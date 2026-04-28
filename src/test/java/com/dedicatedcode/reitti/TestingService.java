package com.dedicatedcode.reitti;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.ApiToken;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.*;
import com.dedicatedcode.reitti.service.ImportProcessor;
import com.dedicatedcode.reitti.service.ImportStateHolder;
import com.dedicatedcode.reitti.service.UserService;
import com.dedicatedcode.reitti.service.importer.GeoJsonImporter;
import com.dedicatedcode.reitti.service.importer.GpxImporter;
import com.dedicatedcode.reitti.service.importer.LocationPointStagingService;
import com.dedicatedcode.reitti.service.importer.PromotionJobHandler;
import com.dedicatedcode.reitti.service.processing.LocationDataIngestPipeline;
import com.dedicatedcode.reitti.service.processing.ProcessingPipelineTrigger;
import com.dedicatedcode.reitti.service.queue.RedisQueueService;
import org.awaitility.Awaitility;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.storage.StorageProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

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
    private RedisQueueService redisQueueService;
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
    private ApiTokenJdbcService apiTokenJdbcService;
    @Autowired
    private LocationDataIngestPipeline locationDataIngestPipeline;
    @Autowired
    private LocationPointStagingService locationPointStagingService;
    @Autowired
    private PromotionJobHandler promotionJobHandler;
    @Autowired
    private JobScheduler jobScheduler;
    @Autowired
    private StorageProvider storageProvider;
    @Autowired
    private ImportJobRepository importJobRepository;

    public void importData(User user, String path) {
        InputStream is = getClass().getResourceAsStream(path);
        if (path.endsWith(".gpx")) {
            gpxImporter.importGpx(is, user, null, null);
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
                                long pendingMessageCount = this.redisQueueService.getQueueSummary().totalPending();
                                return pendingMessageCount == 0;
                            });

                    long runningJobs = Stream.of(StateName.AWAITING, StateName.ENQUEUED, StateName.PROCESSING, StateName.SCHEDULED)
                            .map(storageProvider::countJobs).reduce(Long::sum).orElseThrow();
                    if (!queuesAreEmpty || runningJobs > 0) {
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

                    if (countsStable && importBatchProcessor.isIdle()) {
                        return stableChecks.incrementAndGet() >= requiredStableChecks;
                    } else {
                        stableChecks.set(0);
                        return false;
                    }
                });
    }

    public void clearData() {
        QUEUES_TO_CHECK.forEach(name -> this.redisQueueService.purgeAllQueues());

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

    public ApiToken createApiToken(User user, String name, Device device) {
        return this.apiTokenJdbcService.save(new ApiToken(user, name, device));
    }
}
