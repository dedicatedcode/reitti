package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.ImportStateHolder;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.jobs.JobType;
import com.dedicatedcode.reitti.service.processing.LocationPointStagingService;
import com.garmin.fit.Decode;
import com.garmin.fit.MesgBroadcaster;
import com.garmin.fit.RecordMesgListener;
import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class FitFileImporter {

    private static final Logger logger = LoggerFactory.getLogger(FitFileImporter.class);

    private final ImportStateHolder stateHolder;
    private final LocationPointStagingService stagingService;
    private final JobDetail promotionTask;
    private final JobSchedulingService jobSchedulingService;
    private final int graceTimeSeconds;

    public FitFileImporter(ImportStateHolder stateHolder,
                           LocationPointStagingService stagingService,
                           @Qualifier("promotionJob") JobDetail promotionTask,
                           JobSchedulingService jobSchedulingService,
                           @Value("${reitti.import.grace-time-seconds:300}") int graceTimeSeconds) {
        this.stateHolder = stateHolder;
        this.stagingService = stagingService;
        this.promotionTask = promotionTask;
        this.jobSchedulingService = jobSchedulingService;
        this.graceTimeSeconds = graceTimeSeconds;
    }

    public Map<String, Object> importFile(InputStream inputStream, User user, Device device, String originalFilename) {
        AtomicInteger processedCount = new AtomicInteger(0);
        try {
            stateHolder.importStarted();
            logger.info("Importing GeoJSON file for user {}", user.getUsername());
            String partitionKey = UUID.randomUUID().toString();
            this.stagingService.ensurePartitionExists(partitionKey);

            List<LocationPoint> batch = new ArrayList<>(stagingService.getBatchSize());

            Decode decode = new Decode();
            MesgBroadcaster mesgBroadcaster = new MesgBroadcaster(decode);
            mesgBroadcaster.addListener((RecordMesgListener) mesg -> {
                if (mesg.getTimestamp() != null && mesg.getPositionLat() != null) {
                    // Extracting your required fields
                    long timestamp = mesg.getTimestamp().getTimestamp();
                    double lat = mesg.getPositionLat() * (180.0 / Math.pow(2, 31));
                    double lon = mesg.getPositionLong() * (180.0 / Math.pow(2, 31));
                    Float altitude = mesg.getAltitude();
                    Short accuracy = mesg.getGpsAccuracy();
                    // Process your data here (e.g., send to pipeline)
                    System.out.println("Time: " + timestamp + " Lat: " + lat + " Lon: " + lon + " Acc: " + accuracy);
                    LocationPoint point = new LocationPoint();
                    point.setTimestamp(Instant.ofEpochMilli(timestamp));
                    point.setLatitude(lat);
                    point.setLongitude(lon);
                    point.setAccuracyMeters(Double.valueOf(accuracy));
                    if (altitude != null) {
                        point.setElevationMeters(Double.valueOf(altitude));
                    } else {
                        point.setElevationMeters(0d);
                    }
                    batch.add(point);
                    processedCount.incrementAndGet();
                    if (batch.size() >= stagingService.getBatchSize()) {
                        stagingService.insertBatch(partitionKey, user, device, batch);
                        batch.clear();
                    }
                }
            });

            if (!decode.isFileFit(inputStream)) {
                return Map.of("success", false, "error", "Invalid Fit file. Could not be decoded.");
            }
            decode.read(inputStream);

            UUID parentJobId = jobSchedulingService.createParentJob(
                    user,
                    JobType.FIT_FILE_IMPORT,
                    "Fit File Import - " + originalFilename
            );


            // Process any remaining locations
            if (!batch.isEmpty()) {
                stagingService.insertBatch(partitionKey, user, device, batch);
            }

            logger.info("Imported and queued {} location points from Fit file for user [{}]", processedCount.get(), user.getUsername());

            JobSchedulingService.Metadata metadata = JobSchedulingService.Metadata.builder()
                    .user(user)
                    .jobType(JobType.FIT_FILE_IMPORT)
                    .friendlyName("Fit File Data Promotion")
                    .build();
            jobSchedulingService.scheduleTask(promotionTask,
                                              new PromotionJobHandler.TaskData(user, device, partitionKey, true).withParentJobId(parentJobId),
                                              Instant.now().plusSeconds(graceTimeSeconds),
                                              metadata);
            if (processedCount.get() == 0) {
                return Map.of("success", false,
                              "error", "No valid location points found in Fit File",
                              "pointsReceived", 0);
            } else {
                return Map.of(
                        "success", true,
                        "message", "Successfully queued " + processedCount.get() + " location points for processing",
                        "pointsReceived", processedCount.get()
                );
            }
        } catch (Exception e) {
            logger.error("Error processing Fit file", e);
            return Map.of("success", false, "error", "Error processing Fit file: " + e.getMessage());
        } finally {
            stateHolder.importFinished();
        }
    }
}
