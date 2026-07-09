package com.dedicatedcode.reitti.controller.api.v2;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.DeviceTokenUser;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
import com.dedicatedcode.reitti.service.importer.FitFileImporter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/fit")
public class FitApiController {

    private final DeviceJdbcService deviceJdbcService;
    private final FitFileImporter fitFileImporter;

    public FitApiController(DeviceJdbcService deviceJdbcService,
                            FitFileImporter fitFileImporter) {
        this.deviceJdbcService = deviceJdbcService;
        this.fitFileImporter = fitFileImporter;
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> importFitFile(
            @AuthenticationPrincipal DeviceTokenUser user,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "device", required = false) Long deviceId) {

        Map<String, Object> response = new HashMap<>();

        Device device = this.deviceJdbcService.find(user, deviceId)
                .orElse(user.getDevice().orElseThrow(() -> new IllegalArgumentException("Device not found")));
        try {
            if (file.isEmpty() || file.getOriginalFilename() == null) {
                response.put("success", false);
                response.put("error", "File is empty");
                return ResponseEntity.badRequest().body(response);
            }

            String filename = file.getOriginalFilename();
            if (!filename.endsWith(".fit")) {
                response.put("success", false);
                response.put("error", "Only Fit files (.fit) are supported");
                return ResponseEntity.badRequest().body(response);
            }

            try (InputStream inputStream = file.getInputStream()) {
                Map<String, Object> result = fitFileImporter.importFile(inputStream, user, device, filename);

                if ((Boolean) result.get("success")) {
                    response.put("success", true);
                    response.put("pointsScheduled", result.get("pointsImported"));
                    response.put("message", "Successfully imported Fit file with " + result.get("pointsImported") + " location points");
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
