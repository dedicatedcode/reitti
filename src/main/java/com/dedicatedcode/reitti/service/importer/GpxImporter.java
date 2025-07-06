package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class GpxImporter {
    
    private static final Logger logger = LoggerFactory.getLogger(GpxImporter.class);
    
    private final ImportBatchProcessor batchProcessor;
    
    public GpxImporter(ImportBatchProcessor batchProcessor) {
        this.batchProcessor = batchProcessor;
    }
    
    public Map<String, Object> importGpx(InputStream inputStream, User user) {
        AtomicInteger processedCount = new AtomicInteger(0);
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(inputStream);
            
            // Normalize the XML structure
            document.getDocumentElement().normalize();
            
            // Get all track points (trkpt) from the GPX file
            NodeList trackPoints = document.getElementsByTagName("trkpt");
            
            List<LocationDataRequest.LocationPoint> batch = new ArrayList<>(batchProcessor.getBatchSize());
            
            // Process each track point
            for (int i = 0; i < trackPoints.getLength(); i++) {
                Element trackPoint = (Element) trackPoints.item(i);
                
                try {
                    LocationDataRequest.LocationPoint point = convertGpxTrackPoint(trackPoint);
                    if (point != null) {
                        batch.add(point);
                        processedCount.incrementAndGet();
                        
                        // Process in batches to avoid memory issues
                        if (batch.size() >= batchProcessor.getBatchSize()) {
                            batchProcessor.sendToQueue(user, batch);
                            batch.clear();
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Error processing GPX track point: {}", e.getMessage());
                    // Continue with next point
                }
            }
            
            // Process any remaining locations
            if (!batch.isEmpty()) {
                batchProcessor.sendToQueue(user, batch);
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
        }
    }
    
    /**
     * Converts a GPX track point to our LocationPoint format
     */
    private LocationDataRequest.LocationPoint convertGpxTrackPoint(Element trackPoint) {
        // Check if we have the required attributes
        if (!trackPoint.hasAttribute("lat") || !trackPoint.hasAttribute("lon")) {
            return null;
        }
        
        LocationDataRequest.LocationPoint point = new LocationDataRequest.LocationPoint();
        
        // Get latitude and longitude
        double latitude = Double.parseDouble(trackPoint.getAttribute("lat"));
        double longitude = Double.parseDouble(trackPoint.getAttribute("lon"));
        
        point.setLatitude(latitude);
        point.setLongitude(longitude);
        
        // Get timestamp from the time element
        NodeList timeElements = trackPoint.getElementsByTagName("time");
        if (timeElements.getLength() > 0) {
            String timeStr = timeElements.item(0).getTextContent();
            if (StringUtils.hasText(timeStr)) {
                point.setTimestamp(timeStr);
            } else {
                return null;
            }
        } else {
            return null;
        }
        
        // Set accuracy - GPX doesn't typically include accuracy, so use a default
        point.setAccuracyMeters(10.0); // Default accuracy of 10 meters
        
        return point;
    }
}
