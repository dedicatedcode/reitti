package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.model.User;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Component
public class GoogleTakeoutImporter {
    
    private static final Logger logger = LoggerFactory.getLogger(GoogleTakeoutImporter.class);
    
    private final ObjectMapper objectMapper;
    private final GoogleRecordsImporter googleRecordsImporter;
    private final GoogleTimelineImporter googleTimelineImporter;
    
    public GoogleTakeoutImporter(
            ObjectMapper objectMapper,
            GoogleRecordsImporter googleRecordsImporter,
            GoogleTimelineImporter googleTimelineImporter) {
        this.objectMapper = objectMapper;
        this.googleRecordsImporter = googleRecordsImporter;
        this.googleTimelineImporter = googleTimelineImporter;
    }
    
    public Map<String, Object> importGoogleTakeout(InputStream inputStream, User user) {
        try {
            // Use Jackson's streaming API to detect the format
            JsonFactory factory = objectMapper.getFactory();
            JsonParser parser = factory.createParser(inputStream);
            
            // Look for either "locations" array (Records format) or "rawSignals" array (Timeline format)
            while (parser.nextToken() != null) {
                if (parser.getCurrentToken() == JsonToken.FIELD_NAME) {
                    String fieldName = parser.currentName();
                    
                    if ("locations".equals(fieldName)) {
                        // Old Records.json format - reset stream and delegate
                        parser.close();
                        return googleRecordsImporter.importGoogleRecords(inputStream, user);
                    } else if ("rawSignals".equals(fieldName)) {
                        // New Timeline format - reset stream and delegate
                        parser.close();
                        return googleTimelineImporter.importGoogleTimeline(inputStream, user);
                    }
                }
            }
            
            parser.close();
            return Map.of("success", false, "error", "Invalid format: neither 'locations' nor 'rawSignals' array found");
            
        } catch (IOException e) {
            logger.error("Error detecting Google Takeout format", e);
            return Map.of("success", false, "error", "Error processing Google Takeout file: " + e.getMessage());
        }
    }
}
