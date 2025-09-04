package com.dedicatedcode.reitti.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class GpxSender {
    
    private static class TrackPoint {
        public final double latitude;
        public final double longitude;
        public final Instant timestamp;
        
        public TrackPoint(double latitude, double longitude, Instant timestamp) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.timestamp = timestamp;
        }
    }
    
    private static class OwntracksMessage {
        public String _type = "location";
        public double acc;
        public double lat;
        public double lon;
        public long tst;

        public OwntracksMessage(double lat, double lon, long tst, double acc) {
            this.lat = lat;
            this.lon = lon;
            this.tst = tst;
        }
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: java -jar gpx-sender.jar <gpx-file> <reitti-url> <api-token> [interval-seconds]");
            System.err.println("Example: java -jar gpx-sender.jar track.gpx http://localhost:8080 your-api-token 15");
            System.exit(1);
        }

        String gpxFile = args[0];
        String reittiUrl = args[1];
        String apiToken = args[2];
        int intervalSeconds = args.length > 3 ? Integer.parseInt(args[3]) : 15;

        try {
            List<TrackPoint> trackPoints = parseGpxFile(gpxFile);
            if (trackPoints.isEmpty()) {
                System.err.println("No track points found in GPX file");
                System.exit(1);
            }

            System.out.println("Loaded " + trackPoints.size() + " track points from " + gpxFile);
            System.out.println("Sending to: " + reittiUrl);
            System.out.println("Interval: " + intervalSeconds + " seconds");

            sendTrackPoints(trackPoints, reittiUrl, apiToken, intervalSeconds);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static List<TrackPoint> parseGpxFile(String gpxFile) throws Exception {
        List<TrackPoint> trackPoints = new ArrayList<>();
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new File(gpxFile));
        
        NodeList trkptNodes = document.getElementsByTagName("trkpt");
        
        for (int i = 0; i < trkptNodes.getLength(); i++) {
            Element trkpt = (Element) trkptNodes.item(i);
            
            double lat = Double.parseDouble(trkpt.getAttribute("lat"));
            double lon = Double.parseDouble(trkpt.getAttribute("lon"));
            
            NodeList timeNodes = trkpt.getElementsByTagName("time");
            Instant timestamp = null;
            if (timeNodes.getLength() > 0) {
                String timeStr = timeNodes.item(0).getTextContent();
                timestamp = Instant.parse(timeStr);
            }
            
            trackPoints.add(new TrackPoint(lat, lon, timestamp));
        }
        
        return trackPoints;
    }

    private static void sendTrackPoints(List<TrackPoint> trackPoints, String reittiUrl, String apiToken, int intervalSeconds) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        // Calculate time adjustments - latest point gets current time
        Instant now = Instant.now();
        TrackPoint lastPoint = trackPoints.get(trackPoints.size() - 1);
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            
            for (int i = 0; i < trackPoints.size(); i++) {
                TrackPoint point = trackPoints.get(i);
                
                // Calculate adjusted timestamp
                Instant adjustedTime;
                if (i == trackPoints.size() - 1) {
                    // Last point gets current time
                    adjustedTime = now;
                } else if (point.timestamp != null && lastPoint.timestamp != null) {
                    // Calculate time difference from last point and subtract from now
                    long durationFromLast = lastPoint.timestamp.getEpochSecond() - point.timestamp.getEpochSecond();
                    adjustedTime = now.minusSeconds(durationFromLast);
                } else {
                    // Fallback: distribute points evenly over the past
                    long secondsBack = (long) (trackPoints.size() - i - 1) * intervalSeconds;
                    adjustedTime = now.minusSeconds(secondsBack);
                }
                
                // Create Owntracks message
                OwntracksMessage message = new OwntracksMessage(
                        point.latitude,
                        point.longitude,
                        adjustedTime.getEpochSecond(),
                        10.0
                );
                
                // Send HTTP request
                String url = reittiUrl + "/api/v1/ingest/owntracks";
                HttpPost post = new HttpPost(url);
                post.setHeader("Authorization", "Bearer " + apiToken);
                post.setHeader("Content-Type", "application/json");
                
                String json = objectMapper.writeValueAsString(message);
                post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
                
                System.out.printf("Sending point %d/%d: lat=%.6f, lon=%.6f, time=%s%n", 
                    i + 1, trackPoints.size(), point.latitude, point.longitude, 
                    adjustedTime.toString());
                
                try {
                    httpClient.execute(post, response -> {
                        int statusCode = response.getCode();
                        if (statusCode >= 200 && statusCode < 300) {
                            System.out.println("✓ Sent successfully");
                        } else {
                            System.err.println("✗ Failed with status: " + statusCode);
                        }
                        return null;
                    });
                } catch (Exception e) {
                    System.err.println("✗ Error sending point: " + e.getMessage());
                }
                
                // Wait before sending next point (except for the last one)
                if (i < trackPoints.size() - 1) {
                    Thread.sleep(intervalSeconds * 1000L);
                }
            }
        }
        
        System.out.println("Finished sending all track points");
    }
}
