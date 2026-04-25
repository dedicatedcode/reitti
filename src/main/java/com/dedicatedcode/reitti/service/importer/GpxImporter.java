package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.ImportProcessor;
import com.dedicatedcode.reitti.service.ImportStateHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class GpxImporter {

    private static final Logger logger = LoggerFactory.getLogger(GpxImporter.class);

    private static final int BATCH_SIZE = 1000;

    private final ImportStateHolder stateHolder;
    private final ImportProcessor batchProcessor;

    public GpxImporter(ImportStateHolder stateHolder, ImportProcessor batchProcessor) {
        this.stateHolder = stateHolder;
        this.batchProcessor = batchProcessor;
    }

    public Map<String, Object> importGpx(InputStream inputStream, User user) {
        AtomicInteger processedCount = new AtomicInteger(0);

        try {
            stateHolder.importStarted();
            logger.info("Importing GPX file for user {}", user.getUsername());

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
                            if (!reader.hasAttribute("lat") || !reader.hasAttribute("lon")) {
                                logger.warn("Track point missing lat or lon attribute, skipping");
                                continue;
                            }

                            currentPoint = new LocationPoint();
                            double latitude = Double.parseDouble(reader.getAttributeValue(null, "lat"));
                            double longitude = Double.parseDouble(reader.getAttributeValue(null, "lon"));
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
                                    batchProcessor.processBatch(user, batch);
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
                batchProcessor.processBatch(user, batch);
            }

            logger.info("Successfully imported and queued {} location points from GPX file for user {}",
                    processedCount.get(), user.getUsername());

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
