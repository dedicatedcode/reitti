package com.dedicatedcode.reitti.controller.api.ingestion.gpslogger;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.DeviceTokenUser;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
import com.dedicatedcode.reitti.service.ContextPathHolder;
import com.dedicatedcode.reitti.service.RequestHelper;
import com.dedicatedcode.reitti.service.importer.GpxImporter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/gpslogger/")
public class GPSLoggerApiController {
    private final ContextPathHolder contextPathHolder;
    private final DeviceJdbcService deviceJdbcService;
    private final GpxImporter gpxImporter;

    public GPSLoggerApiController(ContextPathHolder contextPathHolder,
                                  DeviceJdbcService deviceJdbcService,
                                  GpxImporter gpxImporter) {
        this.contextPathHolder = contextPathHolder;
        this.deviceJdbcService = deviceJdbcService;
        this.gpxImporter = gpxImporter;
    }

    @PostMapping(path = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> importGpx(@AuthenticationPrincipal DeviceTokenUser user,
                                                         @RequestParam(required = false) Long device,
                                                         @RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();

        try {
            if (file.isEmpty() || file.getOriginalFilename() == null) {
                response.put("success", false);
                response.put("error", "File is empty");
                return ResponseEntity.badRequest().body(response);
            }

            if (file.getOriginalFilename().equals("gpslogger_test.xml")) {
                response.put("success", true);
                response.put("message", "Successfully received test file");

                return ResponseEntity.ok().body(response);
            }

            if (!file.getOriginalFilename().toLowerCase().endsWith(".gpx")) {
                response.put("success", false);
                response.put("error", "Only GPX files are supported");
                return ResponseEntity.badRequest().body(response);
            }
            Device requestedDevice;
            if (device == null) {
                requestedDevice = user.getDevice().orElse(null);
            } else {
                requestedDevice = this.deviceJdbcService.find(user, device).orElse(null);
                if (requestedDevice == null) {
                    response.put("success", false);
                    response.put("error", "Requested device not found");
                    return ResponseEntity.badRequest().body(response);
                }
            }
            if (requestedDevice == null) {
                response.put("error", "Token has no device attached. Please use another token or attach a device to it.");
                return ResponseEntity.badRequest().body(response);
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

    @GetMapping("/reitti.properties")
    public ResponseEntity<String> getGpsLoggerProperties(@RequestParam String token, HttpServletRequest request) {
        String serverUrl = RequestHelper.getBaseUrl(request);
        String url = serverUrl + contextPathHolder.getContextPath() + "/api/v2/gpslogger/file";
        String properties = "httpfileupload_url=" + url + "\n" +
                "httpfileupload_method=POST\n" +
                "autosend_sendzip=false\n" +
                "autohttpfileupload_enabled=true\n" +
                "httpfileupload_basicauth_password=\n" +
                "httpfileupload_body_type=form-data\n" +
                "httpfileupload_basicauth_username=\n" +
                "httpfileupload_headers=Authorization: " + token + "\n" +
                "autosend_frequency_minutes=60\n" +
                "accuracy_before_logging=25\n" +
                "time_before_logging=15\n" +
                "autosend_enabled=true\n";
        return ResponseEntity.ok()
                .header("Content-Type", "text/plain")
                .body(properties);
    }

}
