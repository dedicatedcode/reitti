package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.model.RawLocationPoint;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.RawLocationPointRepository;
import com.dedicatedcode.reitti.service.ImportHandler;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
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
            @RequestParam("file") MultipartFile file) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) authentication.getPrincipal();

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "File is empty"));
        }

        if (!file.getOriginalFilename().endsWith(".gpx")) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Only GPX files are supported"));
        }

        try (InputStream inputStream = file.getInputStream()) {
            Map<String, Object> result = importHandler.importGpx(inputStream, user);

            if ((Boolean) result.get("success")) {
                return ResponseEntity.accepted().body(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
        } catch (IOException e) {
            logger.error("Error processing GPX file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Error processing file: " + e.getMessage()));
        }
    }

}
