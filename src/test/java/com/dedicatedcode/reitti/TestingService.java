package com.dedicatedcode.reitti;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitRepository;
import com.dedicatedcode.reitti.repository.RawLocationPointRepository;
import com.dedicatedcode.reitti.repository.TripRepository;
import com.dedicatedcode.reitti.repository.VisitRepository;
import com.dedicatedcode.reitti.service.ImportHandler;
import com.dedicatedcode.reitti.service.UserService;
import com.dedicatedcode.reitti.service.processing.RawLocationPointProcessingTrigger;
import org.awaitility.Awaitility;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
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
    private UserService userService;
    @Autowired
    private ImportHandler importHandler;
   @Autowired
    private RawLocationPointRepository rawLocationPointRepository;
    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private TripRepository tripRepository;
    @Autowired
    private ProcessedVisitRepository processedVisitRepository;
    @Autowired
    private VisitRepository visitRepository;
    @Autowired
    private RawLocationPointProcessingTrigger trigger;

    public void importData(String path) {
        User admin = userService.getUserById(1L);
        InputStream is = getClass().getResourceAsStream(path);
        if (path.endsWith(".gpx")) {
            importHandler.importGpx(is, admin);
        } else if (path.endsWith(".geojson")) {
            importHandler.importGeoJson(is, admin);
        } else {
            throw new IllegalStateException("Unsupported file type: " + path);
        }
    }

    public void triggerProcessingPipeline() {
        trigger.start();
    }
    public void awaitDataImport(int seconds) {
        this.lastRun.set(0);
        Awaitility.await()
                .pollInterval(10, TimeUnit.SECONDS)
                .atMost(seconds, TimeUnit.SECONDS)
                .alias("Wait for Queues to be empty").until(() -> {
                    boolean queuesArEmpty = QUEUES_TO_CHECK.stream().allMatch(name -> this.rabbitAdmin.getQueueInfo(name).getMessageCount() == 0);
                    if (!queuesArEmpty){
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
}
