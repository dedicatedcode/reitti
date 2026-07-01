package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.ImportStateHolder;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.jobs.JobType;
import com.dedicatedcode.reitti.service.processing.LocationPointStagingService;
import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class GpxImporter {

    private static final Logger logger = LoggerFactory.getLogger(GpxImporter.class);

    private static final int BATCH_SIZE = 1000;

    private final ImportStateHolder stateHolder;
    private final LocationPointStagingService stagingService;
    private final JobDetail promotionTask;
    private final JobSchedulingService jobSchedulingService;
    private final int graceTimeSeconds;

    public GpxImporter(ImportStateHolder stateHolder,
                       LocationPointStagingService stagingService,
                       @Qualifier("promotionJob") JobDetail promotionTask,
                       @Value("${reitti.import.grace-time-seconds:300}") int graceTimeSeconds,
                       JobSchedulingService jobSchedulingService) {
        this.stateHolder = stateHolder;
        this.stagingService = stagingService;
        this.promotionTask = promotionTask;
        this.graceTimeSeconds = graceTimeSeconds;
        this.jobSchedulingService = jobSchedulingService;
    }

    public Map<String, Object> importGpx(InputStream inputStream, User user, Device device, String originalFilename) {
        AtomicInteger processedCount = new AtomicInteger(0);

        UUID parentJobId = null;
        String partitionKey = null;
        try {
            stateHolder.importStarted();
            logger.info("Importing GPX file for user {}", user.getUsername());
            parentJobId = jobSchedulingService.createParentJob(
                    user,
                    JobType.GPX_IMPORT,
                    "GPX Import - " + originalFilename
            );
            partitionKey = UUID.randomUUID().toString();
            stagingService.ensurePartitionExists(partitionKey);

            XMLInputFactory factory = XMLInputFactory.newInstance();
            // Disable external entity processing for security
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);

            XMLStreamReader reader = factory.createXMLStreamReader(inputStream);

            List<LocationPoint> batch = new ArrayList<>();
            LocationPoint currentPoint = null;
            StringBuilder currentText = new StringBuilder();

            Double currentAccuracyValue = null;
            Double currentHdopValue = null;

            while (reader.hasNext()) {
                int event = reader.next();

                switch (event) {
                    case XMLStreamConstants.START_ELEMENT:
                        String elementName = reader.getLocalName();
                        currentText.setLength(0);

                        if ("trkpt".equals(elementName)) {
                            String latAttr = reader.getAttributeValue(null, "lat");
                            String lonAttr = reader.getAttributeValue(null, "lon");

                            if (latAttr == null || lonAttr == null) {
                                logger.warn("Track point missing lat or lon attribute, skipping");
                                continue;
                            }

                            currentPoint = new LocationPoint();
                            double latitude = Double.parseDouble(latAttr);
                            double longitude = Double.parseDouble(lonAttr);
                            currentPoint.setLatitude(latitude);
                            currentPoint.setLongitude(longitude);

                            currentAccuracyValue = null;
                            currentHdopValue = null;
                        }
                        break;

                    case XMLStreamConstants.CHARACTERS:
                        if (currentPoint != null) {
                            currentText.append(reader.getText());
                        }
                        break;

                    case XMLStreamConstants.END_ELEMENT:
                        String endElementName = reader.getLocalName();

                        if ("trkpt".equals(endElementName) && currentPoint != null) {
                            // Finished processing this track point

                            if (currentPoint.getTimestamp() != null) {
                                // Determine accuracy from optional <accuracy> or <hdop>
                                double finalAccuracy;
                                if (currentAccuracyValue != null) {
                                    finalAccuracy = currentAccuracyValue;
                                } else if (currentHdopValue != null) {
                                    // Map HDOP to metres. Values above 5 indicate poor accuracy.
                                    finalAccuracy = currentHdopValue > 5.0 ? 120.0 : 10.0;
                                } else {
                                    finalAccuracy = 10.0; // default
                                }
                                currentPoint.setAccuracyMeters(finalAccuracy);

                                batch.add(currentPoint);
                                processedCount.incrementAndGet();

                                // Process in batches to avoid memory issues
                                if (batch.size() >= BATCH_SIZE) {
                                    stagingService.insertBatch(partitionKey, user, device, batch);
                                    batch.clear();
                                }
                            } else {
                                logger.warn("Track point missing timestamp, skipping");
                            }
                            currentPoint = null;
                        } else if ("time".equals(endElementName) && currentPoint != null) {
                            String timeStr = currentText.toString().trim();
                            if (StringUtils.hasText(timeStr)) {
                                currentPoint.setTimestamp(Instant.parse(timeStr));
                            }
                        } else if ("ele".equals(endElementName) && currentPoint != null) {
                            String elevationStr = currentText.toString().trim();
                            if (StringUtils.hasText(elevationStr)) {
                                try {
                                    double elevation = Double.parseDouble(elevationStr);
                                    currentPoint.setElevationMeters(elevation);
                                } catch (NumberFormatException e) {
                                    // Ignore invalid elevation values
                                }
                            }
                        } else if ("accuracy".equals(endElementName) && currentPoint != null) {
                            String accStr = currentText.toString().trim();
                            if (StringUtils.hasText(accStr)) {
                                try {
                                    currentAccuracyValue = Double.parseDouble(accStr);
                                } catch (NumberFormatException ignored) {
                                    // invalid accuracy value, keep null
                                }
                            }
                        } else if ("hdop".equals(endElementName) && currentPoint != null) {
                            String hdopStr = currentText.toString().trim();
                            if (StringUtils.hasText(hdopStr)) {
                                try {
                                    currentHdopValue = Double.parseDouble(hdopStr);
                                } catch (NumberFormatException ignored) {
                                    // invalid hdop value, keep null
                                }
                            }
                        }
                        break;
                }
            }

            reader.close();

            // Process any remaining locations
            if (!batch.isEmpty()) {
                stagingService.insertBatch(partitionKey, user, device, batch);
            }

            logger.info("Successfully imported and queued [{}] location points from GPX file for user [{}]", processedCount.get(), user.getUsername());
            JobSchedulingService.Metadata metadata = JobSchedulingService.Metadata.builder()
                    .user(user)
                    .jobType(JobType.GPX_IMPORT)
                    .friendlyName("GPS Data Promotion")
                    .build();
            jobSchedulingService.scheduleTask(promotionTask,
                                              new PromotionJobHandler.TaskData(user, device, partitionKey, true).withParentJobId(parentJobId),
                                              Instant.now().plusSeconds(graceTimeSeconds),
                                              metadata);

            return Map.of(
                    "success", true,
                    "message", "Successfully queued " + processedCount.get() + " location points for processing",
                    "pointsReceived", processedCount.get()
            );

        } catch (Exception e) {
            if (parentJobId != null) {
                this.jobSchedulingService.cancel(parentJobId);
                this.stagingService.dropPartition(partitionKey);
            }
            logger.error("Error processing GPX file", e);
            return Map.of("success", false, "error", "Error processing GPX file: " + e.getMessage());
        } finally {
            stateHolder.importFinished();
        }
    }
}
