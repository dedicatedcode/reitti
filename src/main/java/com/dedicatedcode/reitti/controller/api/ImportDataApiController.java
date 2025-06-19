package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.service.ImportHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ImportDataApiController {

    private static final Logger logger = LoggerFactory.getLogger(ImportDataApiController.class);

    private final ImportHandler importHandler;

    @Autowired
    public ImportDataApiController(ImportHandler importHandler) {
        this.importHandler = importHandler;
    }

    @PostMapping("/import/google-takeout")
    public ResponseEntity<?> importGoogleTakeout(
            @RequestParam("file") MultipartFile file) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) authentication.getPrincipal();

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "File is empty"));
        }

        if (!file.getOriginalFilename().endsWith(".json")) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Only JSON files are supported"));
        }

        try (InputStream inputStream = file.getInputStream()) {
            Map<String, Object> result = importHandler.importGoogleTakeout(inputStream, user);

            if ((Boolean) result.get("success")) {
                return ResponseEntity.accepted().body(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
        } catch (IOException e) {
            logger.error("Error processing Google Takeout file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Error processing file: " + e.getMessage()));
        }
    }

    @PostMapping("/import/gpx")
    public ResponseEntity<?> importGpx(
            @RequestParam("files") MultipartFile[] files) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) authentication.getPrincipal();

        if (files.length == 0) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "No files provided"));
        }

        int totalProcessed = 0;
        int successCount = 0;
        List<String> errors = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                errors.add("File " + file.getOriginalFilename() + " is empty");
                continue;
            }

            if (!file.getOriginalFilename().endsWith(".gpx")) {
                errors.add("File " + file.getOriginalFilename() + " is not a GPX file");
                continue;
            }

            try (InputStream inputStream = file.getInputStream()) {
                Map<String, Object> result = importHandler.importGpx(inputStream, user);

                if ((Boolean) result.get("success")) {
                    totalProcessed += (Integer) result.get("pointsReceived");
                    successCount++;
                } else {
                    errors.add("Error processing " + file.getOriginalFilename() + ": " + result.get("error"));
                }
            } catch (IOException e) {
                logger.error("Error processing GPX file: " + file.getOriginalFilename(), e);
                errors.add("Error processing " + file.getOriginalFilename() + ": " + e.getMessage());
            }
        }

        Map<String, Object> response = new HashMap<>();
        if (successCount > 0) {
            response.put("success", true);
            response.put("message", "Successfully processed " + successCount + " file(s) with " + totalProcessed + " location points");
            response.put("pointsReceived", totalProcessed);
            response.put("filesProcessed", successCount);
            if (!errors.isEmpty()) {
                response.put("errors", errors);
            }
            return ResponseEntity.accepted().body(response);
        } else {
            response.put("success", false);
            response.put("error", "No files were processed successfully");
            response.put("errors", errors);
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/import/geojson")
    public ResponseEntity<?> importGeoJson(
            @RequestParam("files") MultipartFile[] files) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) authentication.getPrincipal();

        if (files.length == 0) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "No files provided"));
        }

        int totalProcessed = 0;
        int successCount = 0;
        List<String> errors = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                errors.add("File " + file.getOriginalFilename() + " is empty");
                continue;
            }

            String filename = file.getOriginalFilename();
            if (filename == null || (!filename.endsWith(".geojson") && !filename.endsWith(".json"))) {
                errors.add("File " + filename + " is not a GeoJSON file");
                continue;
            }

            try (InputStream inputStream = file.getInputStream()) {
                Map<String, Object> result = importHandler.importGeoJson(inputStream, user);

                if ((Boolean) result.get("success")) {
                    totalProcessed += (Integer) result.get("pointsReceived");
                    successCount++;
                } else {
                    errors.add("Error processing " + filename + ": " + result.get("error"));
                }
            } catch (IOException e) {
                logger.error("Error processing GeoJSON file: " + filename, e);
                errors.add("Error processing " + filename + ": " + e.getMessage());
            }
        }

        Map<String, Object> response = new HashMap<>();
        if (successCount > 0) {
            response.put("success", true);
            response.put("message", "Successfully processed " + successCount + " file(s) with " + totalProcessed + " location points");
            response.put("pointsReceived", totalProcessed);
            response.put("filesProcessed", successCount);
            if (!errors.isEmpty()) {
                response.put("errors", errors);
            }
            return ResponseEntity.accepted().body(response);
        } else {
            response.put("success", false);
            response.put("error", "No files were processed successfully");
            response.put("errors", errors);
            return ResponseEntity.badRequest().body(response);
        }
    }

}
