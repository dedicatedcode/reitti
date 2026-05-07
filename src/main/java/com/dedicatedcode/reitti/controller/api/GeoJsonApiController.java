package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.GeoJsonExportService;
import com.dedicatedcode.reitti.service.importer.GeoJsonImporter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/geojson")
public class GeoJsonApiController {

    private final GeoJsonExportService geoJsonExportService;
    private final GeoJsonImporter geoJsonImporter;

    public GeoJsonApiController(GeoJsonExportService geoJsonExportService,
                                GeoJsonImporter geoJsonImporter) {
        this.geoJsonExportService = geoJsonExportService;
        this.geoJsonImporter = geoJsonImporter;
    }

    /**
     * Export location data as GeoJSON FeatureCollection.
     *
     * @param user  authenticated user
     * @param start start instant (ISO-8601, e.g. 2025-01-01T00:00:00Z)
     * @param end   end instant (ISO-8601, e.g. 2025-01-31T23:59:59Z)
     * @return streaming response with GeoJSON content
     */
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> exportGeoJson(
            @AuthenticationPrincipal User user,
            @RequestParam Instant start,
            @RequestParam Instant end) {

        try {
            StreamingResponseBody stream = outputStream -> {
                try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                    geoJsonExportService.generateGeoJsonStreaming(user, start, end, writer);
                } catch (Exception e) {
                    throw new RuntimeException("Error generating GeoJSON file", e);
                }
            };

            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf("application/geo+json"))
                    .body(stream);

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(outputStream -> {
                        try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                            writer.write("{\"error\": \"Error generating GeoJSON file: " + e.getMessage() + "\"}");
                        } catch (IOException ioException) {
                            throw new RuntimeException(ioException);
                        }
                    });
        }
    }

    /**
     * Import a GeoJSON file.
     *
     * @param user   authenticated user
     * @param file   the uploaded file (must end with .geojson)
     * @param device optional device identifier (nullable)
     * @return JSON map with success/error information
     */
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importGeoJson(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "device", required = false) String device) {

        Map<String, Object> response = new HashMap<>();

        try {
            if (file.isEmpty() || file.getOriginalFilename() == null) {
                response.put("success", false);
                response.put("error", "File is empty");
                return ResponseEntity.badRequest().body(response);
            }

            if (!file.getOriginalFilename().endsWith(".geojson")) {
                response.put("success", false);
                response.put("error", "Only GeoJSON files are supported");
                return ResponseEntity.badRequest().body(response);
            }

            try (InputStream inputStream = file.getInputStream()) {
                Map<String, Object> result = geoJsonImporter.importGeoJson(
                        inputStream, user, device, file.getOriginalFilename());

                if ((Boolean) result.get("success")) {
                    response.put("success", true);
                    response.put("pointsScheduled", result.get("pointsReceived"));
                    response.put("message", "Successfully imported GeoJSON file with "
                            + result.get("pointsReceived") + " location points");
                } else {
                    response.put("success", false);
                    response.put("error", result.get("error"));
                }

                return ResponseEntity.ok(response);
            }

        } catch (IOException e) {
            response.put("success", false);
            response.put("error", "Error processing file: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", "Unexpected error: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}