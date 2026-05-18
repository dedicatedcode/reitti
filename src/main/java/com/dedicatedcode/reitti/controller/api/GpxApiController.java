package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
import com.dedicatedcode.reitti.service.GpxExportService;
import com.dedicatedcode.reitti.service.importer.GpxImporter;
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
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/gpx")
public class GpxApiController {

    private final DeviceJdbcService deviceJdbcService;
    private final GpxExportService gpxExportService;
    private final GpxImporter gpxImporter;

    public GpxApiController(DeviceJdbcService deviceJdbcService,
                            GpxExportService gpxExportService,
                            GpxImporter gpxImporter) {
        this.deviceJdbcService = deviceJdbcService;
        this.gpxExportService = gpxExportService;
        this.gpxImporter = gpxImporter;
    }

    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> exportGpx(@AuthenticationPrincipal User user,
                                                           @RequestParam(required = false) Long device,
                                                          @RequestParam LocalDate start,
                                                          @RequestParam LocalDate end) {
        try {
            Device requestedDevice = device == null ? null : this.deviceJdbcService.find(user, device).orElseThrow(IllegalArgumentException::new);
            StreamingResponseBody stream = outputStream -> {
                try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                    gpxExportService.generateGpxContentStreaming(user,
                                                                 requestedDevice,
                                                                 ZonedDateTime.of(start.atStartOfDay(), ZoneId.of("UTC")).toInstant(),
                                                                 ZonedDateTime.of(end.atStartOfDay(), ZoneId.of("UTC")).toInstant(),
                                                                 writer,
                                                                 false);
                } catch (Exception e) {
                    throw new RuntimeException("Error generating GPX file", e);
                }
            };
            
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(stream);
                
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(outputStream -> {
                    try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                        writer.write("Error generating GPX file: " + e.getMessage());
                    } catch (IOException ioException) {
                        throw new RuntimeException(ioException);
                    }
                });
        }
    }
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importGpx(@AuthenticationPrincipal User user,
                                                         @RequestParam(required = false) Long device,
                                                         @RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (file.isEmpty() || file.getOriginalFilename() == null) {
                response.put("success", false);
                response.put("error", "File is empty");
                return ResponseEntity.badRequest().body(response);
            }

            if (!file.getOriginalFilename().endsWith(".gpx")) {
                response.put("success", false);
                response.put("error", "Only GPX files are supported");
                return ResponseEntity.badRequest().body(response);
            }
            Device requestedDevice;
            if (device == null) {
                requestedDevice = null;
            } else {
                requestedDevice = this.deviceJdbcService.find(user, device).orElse(null);
                if (requestedDevice == null) {
                    response.put("success", false);
                    response.put("error", "Requested device not found");
                    return ResponseEntity.badRequest().body(response);
                }
            }

            try (InputStream inputStream = file.getInputStream()) {
                Map<String, Object> result = gpxImporter.importGpx(inputStream, user, requestedDevice, file.getOriginalFilename());
                
                if ((Boolean) result.get("success")) {
                    response.put("success", true);
                    response.put("pointsScheduled", result.get("pointsReceived"));
                    response.put("message", "Successfully imported GPX file with " + result.get("pointsReceived") + " location points");
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
