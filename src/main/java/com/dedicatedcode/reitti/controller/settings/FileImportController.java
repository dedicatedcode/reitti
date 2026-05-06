package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
import com.dedicatedcode.reitti.service.I18nService;
import com.dedicatedcode.reitti.service.importer.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Controller
@RequestMapping("/settings/import")
public class FileImportController {

    private final GpxImporter gpxImporter;
    private final GoogleRecordsImporter googleRecordsImporter;
    private final GoogleAndroidTimelineImporter googleAndroidTimelineImporter;
    private final GoogleIOSTimelineImporter googleTimelineIOSImporter;
    private final GeoJsonImporter geoJsonImporter;
    private final DeviceJdbcService deviceJdbcService;
    private final I18nService i18n;
    private final boolean dataManagementEnabled;
    private final int maxFileSupported;
    private final String maxFileSize;

    public FileImportController(GpxImporter gpxImporter,
                                GoogleRecordsImporter googleRecordsImporter,
                                GoogleAndroidTimelineImporter googleAndroidTimelineImporter,
                                GoogleIOSTimelineImporter googleTimelineIOSImporter,
                                GeoJsonImporter geoJsonImporter, DeviceJdbcService deviceJdbcService,
                                I18nService i18n,
                                @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled,
                                @Value("${server.tomcat.max-part-count}") int maxFileSupported,
                                @Value("${spring.servlet.multipart.max-file-size}") String maxFileSize) {
        this.gpxImporter = gpxImporter;
        this.googleRecordsImporter = googleRecordsImporter;
        this.googleAndroidTimelineImporter = googleAndroidTimelineImporter;
        this.googleTimelineIOSImporter = googleTimelineIOSImporter;
        this.geoJsonImporter = geoJsonImporter;
        this.deviceJdbcService = deviceJdbcService;
        this.i18n = i18n;
        this.dataManagementEnabled = dataManagementEnabled;
        this.maxFileSupported = maxFileSupported;
        this.maxFileSize = maxFileSize;
    }

    @GetMapping
    public String getFileUploadPage(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("activeSection", "file-upload");
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        model.addAttribute("devices", this.deviceJdbcService.getAll(user));
        return "settings/import-data";
    }

    @PostMapping("/gpx")
    public String importGpx(@RequestParam("files") MultipartFile[] files,
                            @RequestParam(required = false, name = "device") Long deviceId,
                            Authentication authentication,
                            Model model) {
        User user = (User) authentication.getPrincipal();
        Device device = deviceId != null ? this.deviceJdbcService.find(user, deviceId).orElseThrow(IllegalArgumentException::new) : null;
        model.addAttribute("devices", this.deviceJdbcService.getAll(user));
        if (files.length == 0) {
            model.addAttribute("uploadErrorMessage", "No files selected");
            return "settings/import-data :: file-upload-content";
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
                Map<String, Object> result = this.gpxImporter.importGpx(inputStream, user, device, file.getOriginalFilename());

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

        return "settings/import-data :: file-upload-content";
    }

    @PostMapping("/google-records")
    public String importGoogleRecords(@RequestParam("file") MultipartFile file,
                                      @RequestParam(required = false, name = "device") Long deviceId,
                                      Authentication authentication,
                                      Model model) {
        User user = (User) authentication.getPrincipal();
        Device device = deviceId != null ? this.deviceJdbcService.find(user, deviceId).orElseThrow(IllegalArgumentException::new) : null;
        model.addAttribute("devices", this.deviceJdbcService.getAll(user));

        if (file.isEmpty() || file.getOriginalFilename() == null) {
            model.addAttribute("uploadErrorMessage", "File is empty");
            return "settings/import-data :: file-upload-content";
        }

        if (!file.getOriginalFilename().endsWith(".json")) {
            model.addAttribute("uploadErrorMessage", "Only JSON files are supported");
            return "settings/import-data :: file-upload-content";
        }

        try (InputStream inputStream = file.getInputStream()) {
            Map<String, Object> result = this.googleRecordsImporter.importGoogleRecords(inputStream, user, device, file.getOriginalFilename());

            if ((Boolean) result.get("success")) {
                model.addAttribute("uploadSuccessMessage", result.get("message"));
            } else {
                model.addAttribute("uploadErrorMessage", result.get("error"));
            }

            return "settings/import-data :: file-upload-content";
        } catch (IOException e) {
            model.addAttribute("uploadErrorMessage", "Error processing file: " + e.getMessage());
            return "settings/import-data :: file-upload-content";
        }
    }

    @PostMapping("/google-timeline-android")
    public String importGoogleTimelineAndroid(@RequestParam("file") MultipartFile file,
                                              @RequestParam(required = false, name = "device") Long deviceId,
                                              Authentication authentication,
                                              Model model) {
        User user = (User) authentication.getPrincipal();
        Device device = deviceId != null ? this.deviceJdbcService.find(user, deviceId).orElseThrow(IllegalArgumentException::new) : null;
        model.addAttribute("devices", this.deviceJdbcService.getAll(user));

        if (file.isEmpty() || file.getOriginalFilename() == null) {
            model.addAttribute("uploadErrorMessage", "File is empty");
            return "settings/import-data :: file-upload-content";
        }

        if (!file.getOriginalFilename().endsWith(".json")) {
            model.addAttribute("uploadErrorMessage", "Only JSON files are supported");
            return "settings/import-data :: file-upload-content";
        }

        try (InputStream inputStream = file.getInputStream()) {
            Map<String, Object> result = this.googleAndroidTimelineImporter.importTimeline(inputStream, user, device, file.getOriginalFilename());

            if ((Boolean) result.get("success")) {
                model.addAttribute("uploadSuccessMessage", result.get("message"));
            } else {
                model.addAttribute("uploadErrorMessage", result.get("error"));
            }

            return "settings/import-data :: file-upload-content";
        } catch (IOException e) {
            model.addAttribute("uploadErrorMessage", "Error processing file: " + e.getMessage());
            return "settings/import-data :: file-upload-content";
        }
    }

    @PostMapping("/google-timeline-ios")
    public String importGoogleTimelineIOS(@RequestParam("file") MultipartFile file,
                                          @RequestParam(required = false, name = "device") Long deviceId,
                                          Authentication authentication,
                                          Model model) {
        User user = (User) authentication.getPrincipal();
        Device device = deviceId != null ? this.deviceJdbcService.find(user, deviceId).orElseThrow(IllegalArgumentException::new) : null;
        model.addAttribute("devices", this.deviceJdbcService.getAll(user));

        if (file.isEmpty() || file.getOriginalFilename() == null) {
            model.addAttribute("uploadErrorMessage", "File is empty");
            return "settings/import-data :: file-upload-content";
        }

        if (!file.getOriginalFilename().endsWith(".json")) {
            model.addAttribute("uploadErrorMessage", "Only JSON files are supported");
            return "settings/import-data :: file-upload-content";
        }

        try (InputStream inputStream = file.getInputStream()) {
            Map<String, Object> result = this.googleTimelineIOSImporter.importTimeline(inputStream, user, device, file.getOriginalFilename());

            if ((Boolean) result.get("success")) {
                model.addAttribute("uploadSuccessMessage", result.get("message"));
            } else {
                model.addAttribute("uploadErrorMessage", result.get("error"));
            }

            return "settings/import-data :: file-upload-content";
        } catch (IOException e) {
            model.addAttribute("uploadErrorMessage", "Error processing file: " + e.getMessage());
            return "settings/import-data :: file-upload-content";
        }
    }

    @PostMapping("/geojson")
    public String importGeoJson(@RequestParam("files") MultipartFile[] files,
                                @RequestParam(required = false, name = "device") Long deviceId,
                                Authentication authentication,
                                Model model) {
        User user = (User) authentication.getPrincipal();
        Device device = deviceId != null ? this.deviceJdbcService.find(user, deviceId).orElseThrow(IllegalArgumentException::new) : null;
        model.addAttribute("devices", this.deviceJdbcService.getAll(user));

        if (files.length == 0) {
            model.addAttribute("uploadErrorMessage", "No files selected");
            return "settings/import-data :: file-upload-content";
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
                Map<String, Object> result = this.geoJsonImporter.importGeoJson(inputStream, user, device, filename);

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

        return "settings/import-data :: file-upload-content";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.OK)
    public String handleMaxUploadSizeExceededException(Model model) {
        model.addAttribute("uploadErrorMessage", i18n.translate("upload.error.max_upload_size_exceeded", maxFileSupported, maxFileSize));
        return "settings/import-data :: file-upload-content";
    }
}
