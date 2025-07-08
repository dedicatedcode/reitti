package com.dedicatedcode.reitti;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.*;
import com.dedicatedcode.reitti.service.importer.GeoJsonImporter;
import com.dedicatedcode.reitti.service.importer.GpxImporter;
import com.dedicatedcode.reitti.service.processing.RawLocationPointProcessingTrigger;
import org.awaitility.Awaitility;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TestingService {

    private static final List<String> QUEUES_TO_CHECK = List.of(
            RabbitMQConfig.MERGE_VISIT_QUEUE,
            RabbitMQConfig.STAY_DETECTION_QUEUE,
            RabbitMQConfig.LOCATION_DATA_QUEUE,
            RabbitMQConfig.DETECT_TRIP_QUEUE,
            RabbitMQConfig.SIGNIFICANT_PLACE_QUEUE
    );

    private final AtomicLong lastRun = new AtomicLong(0);

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
    private RawLocationPointProcessingTrigger trigger;

    public void importData(String path) {
        User admin = userJdbcService.findById(1L)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + (Long) 1L));
        InputStream is = getClass().getResourceAsStream(path);
        if (path.endsWith(".gpx")) {
            gpxImporter.importGpx(is, admin);
        } else if (path.endsWith(".geojson")) {
            geoJsonImporter.importGeoJson(is, admin);
        } else {
            throw new IllegalStateException("Unsupported file type: " + path);
        }
    }

    public User admin() {
        return this.userJdbcService.findById(1L)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + (Long) 1L));
    }

    public void triggerProcessingPipeline(int timeoout) {
        trigger.start();
        awaitDataImport(timeoout);
    }

    public void awaitDataImport(int seconds) {
        this.lastRun.set(0);
        Awaitility.await()
                .pollInterval(seconds / 10, TimeUnit.SECONDS)
                .atMost(seconds, TimeUnit.SECONDS)
                .alias("Wait for Queues to be empty").until(() -> {
                    boolean queuesArEmpty = QUEUES_TO_CHECK.stream().allMatch(name -> this.rabbitAdmin.getQueueInfo(name).getMessageCount() == 0);
                    if (!queuesArEmpty) {
                        return false;
                    }

                    long currentCount = rawLocationPointRepository.count();
                    return currentCount == lastRun.getAndSet(currentCount);
                });
    }

    public void clearData() {
        //first, purge all messages from rabbit mq
        lastRun.set(0);
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

    public void importAndProcess(String path) {
        importData(path);
        awaitDataImport(10);
        triggerProcessingPipeline(20);
    }
}
