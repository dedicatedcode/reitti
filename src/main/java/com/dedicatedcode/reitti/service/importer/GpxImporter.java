package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ImportJobRepository;
import com.dedicatedcode.reitti.service.ImportStateHolder;
import com.dedicatedcode.reitti.service.jobs.JobState;
import org.jobrunr.scheduling.JobScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
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
    private final PromotionJobHandler promotionJobHandler;
    private final ImportJobRepository importJobRepository;
    private final int graceTimeSeconds;
    private final JobScheduler jobScheduler;

    public GpxImporter(ImportStateHolder stateHolder,
                       LocationPointStagingService stagingService,
                       PromotionJobHandler promotionJobHandler, ImportJobRepository importJobRepository,
                       @Value("${reitti.import.processing-idle-start-time}") int graceTimeSeconds,
                       JobScheduler jobScheduler) {
        this.stateHolder = stateHolder;
        this.stagingService = stagingService;
        this.promotionJobHandler = promotionJobHandler;
        this.importJobRepository = importJobRepository;
        this.graceTimeSeconds = graceTimeSeconds;
        this.jobScheduler = jobScheduler;
    }

    public Map<String, Object> importGpx(InputStream inputStream, User user, Device device, String originalFilename) {
        AtomicInteger processedCount = new AtomicInteger(0);

        try {
            stateHolder.importStarted();
            logger.info("Importing GPX file for user {}", user.getUsername());

            UUID jobId = UUID.randomUUID();
            stagingService.start(jobId, user, originalFilename);

            XMLInputFactory factory = XMLInputFactory.newInstance();
            // Disable external entity processing for security
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);

            XMLStreamReader reader = factory.createXMLStreamReader(inputStream);

            List<LocationPoint> batch = new ArrayList<>();
            LocationPoint currentPoint = null;
            StringBuilder currentText = new StringBuilder();

            while (reader.hasNext()) {
                int event = reader.next();

                switch (event) {
                    case XMLStreamConstants.START_ELEMENT:
                        String elementName = reader.getLocalName();
                        currentText.setLength(0);

                        if ("trkpt".equals(elementName)) {
                            // Start of a new track point
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
                            // Default accuracy for GPX files
                            currentPoint.setAccuracyMeters(10.0);
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
                                batch.add(currentPoint);
                                processedCount.incrementAndGet();

                                // Process in batches to avoid memory issues
                                if (batch.size() >= BATCH_SIZE) {
                                    stagingService.insertBatch(jobId, user, device, batch);
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
                        }
                        break;
                }
            }

            reader.close();

            // Process any remaining locations
            if (!batch.isEmpty()) {
                stagingService.insertBatch(jobId, user, device, batch);
            }

            logger.info("Successfully imported and queued [{}] location points from GPX file for user [{}]", processedCount.get(), user.getUsername());
            importJobRepository.updateState(jobId, JobState.AWAITING);
            if (graceTimeSeconds > 0) {
                jobScheduler.schedule(LocalDateTime.now().plusSeconds(graceTimeSeconds), () -> promotionJobHandler.execute(user, device, jobId));
            } else {
                jobScheduler.enqueue(() -> promotionJobHandler.execute(user, device, jobId));
            }
            return Map.of(
                    "success", true,
                    "message", "Successfully queued " + processedCount.get() + " location points for processing",
                    "pointsReceived", processedCount.get()
            );

        } catch (Exception e) {
            logger.error("Error processing GPX file", e);
            return Map.of("success", false, "error", "Error processing GPX file: " + e.getMessage());
        } finally {
            stateHolder.importFinished();
        }
    }
}
