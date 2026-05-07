package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/geojson")
public class GeoJsonApiController {

    private final GeoJsonExportService geoJsonExportService;
    private final DeviceJdbcService deviceJdbcService;
    private final GeoJsonImporter geoJsonImporter;

    public GeoJsonApiController(GeoJsonExportService geoJsonExportService, DeviceJdbcService deviceJdbcService,
                                GeoJsonImporter geoJsonImporter) {
        this.geoJsonExportService = geoJsonExportService;
        this.deviceJdbcService = deviceJdbcService;
        this.geoJsonImporter = geoJsonImporter;
    }

    @GetMapping(value = "/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamingResponseBody> exportGeoJson(
            @AuthenticationPrincipal User user,
            @RequestParam("start") LocalDate start,
            @RequestParam("end") LocalDate end,
            @RequestParam(value = "device", required = false) Long deviceId) {

        try {
            StreamingResponseBody stream = outputStream -> {
                try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                    geoJsonExportService.generateGeoJsonContentStreaming(
                            user,
                            ZonedDateTime.of(start.atStartOfDay(), ZoneId.of("UTC")).toInstant(),
                            ZonedDateTime.of(end.atStartOfDay(), ZoneId.of("UTC")).toInstant(),
                            deviceId,
                            writer);
                } catch (Exception e) {
                    throw new RuntimeException("Error generating GeoJSON file", e);
                }
            };

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(stream);

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(outputStream -> {
                        try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                            writer.write("Error generating GeoJSON file: " + e.getMessage());
                        } catch (IOException ioException) {
                            throw new RuntimeException(ioException);
                        }
                    });
        }
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> importGeoJson(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "device", required = false) Long deviceId) {

        Map<String, Object> response = new HashMap<>();

        Device device = this.deviceJdbcService.find(user, deviceId).orElse(null);
        try {
            if (file.isEmpty() || file.getOriginalFilename() == null) {
                response.put("success", false);
                response.put("error", "File is empty");
                return ResponseEntity.badRequest().body(response);
            }

            String filename = file.getOriginalFilename();
            if (!filename.endsWith(".geojson") && !filename.endsWith(".json")) {
                response.put("success", false);
                response.put("error", "Only GeoJSON files (.geojson or .json) are supported");
                return ResponseEntity.badRequest().body(response);
            }

            try (InputStream inputStream = file.getInputStream()) {
                Map<String, Object> result = geoJsonImporter.importGeoJson(
                        inputStream, user, device, filename);

                if ((Boolean) result.get("success")) {
                    response.put("success", true);
                    response.put("pointsScheduled", result.get("pointsImported"));
                    response.put("message", "Successfully imported GeoJSON file with "
                            + result.get("pointsImported") + " location points");
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
