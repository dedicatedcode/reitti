package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.service.importer.*;
import com.dedicatedcode.reitti.service.processing.ProcessingPipelineTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Controller
@RequestMapping("/import")
public class FileImportController {

    private static final Logger logger = LoggerFactory.getLogger(FileImportController.class);
    
    private final GpxImporter gpxImporter;
    private final GoogleRecordsImporter googleRecordsImporter;
    private final GoogleAndroidTimelineImporter googleAndroidTimelineImporter;
    private final GoogleIOSTimelineImporter googleTimelineIOSImporter;
    private final GeoJsonImporter geoJsonImporter;

    public FileImportController(GpxImporter gpxImporter,
                                GoogleRecordsImporter googleRecordsImporter,
                                GoogleAndroidTimelineImporter googleAndroidTimelineImporter,
                                GoogleIOSTimelineImporter googleTimelineIOSImporter, GeoJsonImporter geoJsonImporter) {
        this.gpxImporter = gpxImporter;
        this.googleRecordsImporter = googleRecordsImporter;
        this.googleAndroidTimelineImporter = googleAndroidTimelineImporter;
        this.googleTimelineIOSImporter = googleTimelineIOSImporter;
        this.geoJsonImporter = geoJsonImporter;
    }

    @GetMapping("/file-upload-content")
    public String getDataImportContent() {
        return "fragments/file-upload :: file-upload-content";
    }

    @PostMapping("/gpx")
    public String importGpx(@RequestParam("files") MultipartFile[] files,
                            Authentication authentication,
                            Model model) {
        User user = (User) authentication.getPrincipal();

        if (files.length == 0) {
            model.addAttribute("uploadErrorMessage", "No files selected");
            return "fragments/file-upload :: file-upload-content";
        }

        int totalProcessed = 0;
        int successCount = 0;
        StringBuilder errorMessages = new StringBuilder();

        for (MultipartFile file : files) {
            if (file.isEmpty() || file.getOriginalFilename() == null) {
                errorMessages.append("File ").append(file.getOriginalFilename()).append(" is empty. ");
                continue;
            }

            if (!file.getOriginalFilename().endsWith(".gpx")) {
                errorMessages.append("File ").append(file.getOriginalFilename()).append(" is not a GPX file. ");
                continue;
            }

            try (InputStream inputStream = file.getInputStream()) {
                Map<String, Object> result = this.gpxImporter.importGpx(inputStream, user);

                if ((Boolean) result.get("success")) {
                    totalProcessed += (Integer) result.get("pointsReceived");
                    successCount++;
                } else {
                    errorMessages.append("Error processing ").append(file.getOriginalFilename()).append(": ")
                            .append(result.get("error")).append(". ");
                }
            } catch (IOException e) {
                errorMessages.append("Error processing ").append(file.getOriginalFilename()).append(": ")
                        .append(e.getMessage()).append(". ");
            }
        }

        if (successCount > 0) {
            String message = "Successfully processed " + successCount + " file(s) with " + totalProcessed + " location points";
            if (!errorMessages.isEmpty()) {
                message += ". Errors: " + errorMessages;
            }
            model.addAttribute("uploadSuccessMessage", message);
        } else {
            model.addAttribute("uploadErrorMessage", "No files were processed successfully. " + errorMessages);
        }

        return "fragments/file-upload :: file-upload-content";
    }

    @PostMapping("/google-records")
    public String importGoogleRecords(@RequestParam("file") MultipartFile file,
                                     Authentication authentication,
                                     Model model) {
        User user = (User) authentication.getPrincipal();

        if (file.isEmpty() || file.getOriginalFilename() == null) {
            model.addAttribute("uploadErrorMessage", "File is empty");
            return "fragments/file-upload :: file-upload-content";
        }

        if (!file.getOriginalFilename().endsWith(".json")) {
            model.addAttribute("uploadErrorMessage", "Only JSON files are supported");
            return "fragments/file-upload :: file-upload-content";
        }

        try (InputStream inputStream = file.getInputStream()) {
            Map<String, Object> result = this.googleRecordsImporter.importGoogleRecords(inputStream, user);

            if ((Boolean) result.get("success")) {
                model.addAttribute("uploadSuccessMessage", result.get("message"));
            } else {
                model.addAttribute("uploadErrorMessage", result.get("error"));
            }

            return "fragments/file-upload :: file-upload-content";
        } catch (IOException e) {
            model.addAttribute("uploadErrorMessage", "Error processing file: " + e.getMessage());
            return "fragments/file-upload :: file-upload-content";
        }
    }

    @PostMapping("/google-timeline-android")
    public String importGoogleTimelineAndroid(@RequestParam("file") MultipartFile file,
                                             Authentication authentication,
                                             Model model) {
        User user = (User) authentication.getPrincipal();

        if (file.isEmpty() || file.getOriginalFilename() == null) {
            model.addAttribute("uploadErrorMessage", "File is empty");
            return "fragments/file-upload :: file-upload-content";
        }

        if (!file.getOriginalFilename().endsWith(".json")) {
            model.addAttribute("uploadErrorMessage", "Only JSON files are supported");
            return "fragments/file-upload :: file-upload-content";
        }

        try (InputStream inputStream = file.getInputStream()) {
            Map<String, Object> result = this.googleAndroidTimelineImporter.importTimeline(inputStream, user);

            if ((Boolean) result.get("success")) {
                model.addAttribute("uploadSuccessMessage", result.get("message"));
            } else {
                model.addAttribute("uploadErrorMessage", result.get("error"));
            }

            return "fragments/file-upload :: file-upload-content";
        } catch (IOException e) {
            model.addAttribute("uploadErrorMessage", "Error processing file: " + e.getMessage());
            return "fragments/file-upload :: file-upload-content";
        }
    }

    @PostMapping("/google-timeline-ios")
    public String importGoogleTimelineIOS(@RequestParam("file") MultipartFile file,
                                         Authentication authentication,
                                         Model model) {
        User user = (User) authentication.getPrincipal();

        if (file.isEmpty() || file.getOriginalFilename() == null) {
            model.addAttribute("uploadErrorMessage", "File is empty");
            return "fragments/file-upload :: file-upload-content";
        }

        if (!file.getOriginalFilename().endsWith(".json")) {
            model.addAttribute("uploadErrorMessage", "Only JSON files are supported");
            return "fragments/file-upload :: file-upload-content";
        }

        try (InputStream inputStream = file.getInputStream()) {
            Map<String, Object> result = this.googleTimelineIOSImporter.importTimeline(inputStream, user);

            if ((Boolean) result.get("success")) {
                model.addAttribute("uploadSuccessMessage", result.get("message"));
            } else {
                model.addAttribute("uploadErrorMessage", result.get("error"));
            }

            return "fragments/file-upload :: file-upload-content";
        } catch (IOException e) {
            model.addAttribute("uploadErrorMessage", "Error processing file: " + e.getMessage());
            return "fragments/file-upload :: file-upload-content";
        }
    }

    @PostMapping("/geojson")
    public String importGeoJson(@RequestParam("files") MultipartFile[] files,
                                Authentication authentication,
                                Model model) {
        User user = (User) authentication.getPrincipal();

        if (files.length == 0) {
            model.addAttribute("uploadErrorMessage", "No files selected");
            return "fragments/file-upload :: file-upload-content";
        }

        int totalProcessed = 0;
        int successCount = 0;
        StringBuilder errorMessages = new StringBuilder();

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                errorMessages.append("File ").append(file.getOriginalFilename()).append(" is empty. ");
                continue;
            }

            String filename = file.getOriginalFilename();
            if (filename == null || (!filename.endsWith(".geojson") && !filename.endsWith(".json"))) {
                errorMessages.append("File ").append(filename).append(" is not a GeoJSON file. ");
                continue;
            }

            try (InputStream inputStream = file.getInputStream()) {
                Map<String, Object> result = this.geoJsonImporter.importGeoJson(inputStream, user);

                if ((Boolean) result.get("success")) {
                    totalProcessed += (Integer) result.get("pointsReceived");
                    successCount++;
                } else {
                    errorMessages.append("Error processing ").append(filename).append(": ")
                            .append(result.get("error")).append(". ");
                }
            } catch (IOException e) {
                errorMessages.append("Error processing ").append(filename).append(": ")
                        .append(e.getMessage()).append(". ");
            }
        }

        if (successCount > 0) {
            String message = "Successfully processed " + successCount + " file(s) with " + totalProcessed + " location points";
            if (!errorMessages.isEmpty()) {
                message += ". Errors: " + errorMessages;
            }
            model.addAttribute("uploadSuccessMessage", message);
        } else {
            model.addAttribute("uploadErrorMessage", "No files were processed successfully. " + errorMessages);
        }

        return "fragments/file-upload :: file-upload-content";
    }
}
