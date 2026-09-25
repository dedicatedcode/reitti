package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.UserType;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.geo.SourceLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import com.dedicatedcode.reitti.service.GpxExportService;
import com.dedicatedcode.reitti.service.TimeUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Controller
@RequestMapping("/settings/export-data")
public class ExportDataController {

    static final String TIMELINE_DEVICE_ID = "timeline";

    private final SourceLocationPointJdbcService sourceLocationPointJdbcService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final DeviceJdbcService deviceJdbcService;
    private final GpxExportService gpxExportService;
    private final boolean dataManagementEnabled;

    public ExportDataController(SourceLocationPointJdbcService sourceLocationPointJdbcService,
                                RawLocationPointJdbcService rawLocationPointJdbcService,
                                DeviceJdbcService deviceJdbcService,
                                GpxExportService gpxExportService,
                                @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled) {
        this.sourceLocationPointJdbcService = sourceLocationPointJdbcService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.deviceJdbcService = deviceJdbcService;
        this.gpxExportService = gpxExportService;
        this.dataManagementEnabled = dataManagementEnabled;
    }


    @GetMapping
    public String getExportDataPage(@AuthenticationPrincipal User user, Model model) {
        if (user.getUserType() == UserType.LIVE_DATA_ONLY) {
            model.addAttribute("activeSection", "export-data");
            model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
            model.addAttribute("dataManagementEnabled", dataManagementEnabled);
            return "settings/unavailable";
        }
        // Set default date range to today
        java.time.LocalDate today = java.time.LocalDate.now();
        model.addAttribute("startDate", today);
        model.addAttribute("endDate", today);
        model.addAttribute("devices", deviceJdbcService.getAll(user));

        // Get raw location points for today by default
        model.addAttribute("rawLocationPoints", Collections.emptyList());
        model.addAttribute("activeSection", "export-data");
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        return "settings/export-data";
    }

    @GetMapping("/data-content")
    public String getExportDataContent(@AuthenticationPrincipal User user,
                                      @RequestParam(required = false) String startDate,
                                      @RequestParam(required = false) String endDate,
                                      @RequestParam(required = false) String deviceId,
                                      @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
                                      @RequestParam(required = false, defaultValue = "0") int page,
                                      @RequestParam(required = false, defaultValue = "100") int size,
                                      Model model) {
        boolean timeline = TIMELINE_DEVICE_ID.equals(deviceId) && dataManagementEnabled;
        Device device = timeline ? null : findDevice(user, deviceId);
        LocalDate start = StringUtils.hasText(startDate) ? LocalDate.parse(startDate) : LocalDate.now();
        LocalDate end = StringUtils.hasText(endDate) ? LocalDate.parse(endDate) : LocalDate.now();
        ZonedDateTime startDateTime = start.atStartOfDay(timezone);
        ZonedDateTime endDateTime = end.plusDays(1).atStartOfDay(timezone);
        model.addAttribute("startDate", start);
        model.addAttribute("endDate", end);

        List<DataLine> paginatedData;
        long totalElements;
        if (TIMELINE_DEVICE_ID.equals(deviceId) && dataManagementEnabled) {
            // The consolidated timeline (raw_location_points) with synthetic points, excluding ignored ones
            List<RawLocationPoint> timelinePoints = rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(user, startDateTime.toInstant(), endDateTime.toInstant(), true, false, page, size);
            totalElements = rawLocationPointJdbcService.countByUserAndTimestampBetween(user, startDateTime.toInstant(), endDateTime.toInstant(), true, false);
            paginatedData = timelinePoints.stream()
                    .map(p -> new DataLine(TimeUtil.adjustInstant(p.getTimestamp(), timezone),
                            p.getLatitude(), p.getLongitude(), p.getAccuracyMeters()))
                    .toList();
        } else {
            List<SourceLocationPoint> allPoints = sourceLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(user, device, startDateTime.toInstant(), endDateTime.toInstant(), true, true, page, size);
            totalElements = sourceLocationPointJdbcService.countByUserAndTimestampBetween(user, device, startDateTime.toInstant(), endDateTime.toInstant(), true, true);
            paginatedData = allPoints.stream()
                    .map(p -> new DataLine(TimeUtil.adjustInstant(p.getTimestamp(), timezone),
                            p.getLatitude(), p.getLongitude(), p.getAccuracyMeters()))
                    .toList();
        }
        int totalPages = (int) Math.ceil((double) totalElements / size);
        
        model.addAttribute("rawLocationPoints", paginatedData);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("totalElements", totalElements);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("hasNext", page < totalPages - 1);
        model.addAttribute("hasPrevious", page > 0);
        
        return "settings/export-data :: data-content";
    }

    private Device findDevice(User user, String deviceId) {
        if (!StringUtils.hasText(deviceId) ) {
            return null;
        } else {
            return deviceJdbcService.find(user, Long.valueOf(deviceId)).orElseThrow(IllegalArgumentException::new);
        }
    }

    @GetMapping("/gpx")
    public ResponseEntity<StreamingResponseBody> exportGpx(@AuthenticationPrincipal User user,
                                                           @RequestParam(required = false) String deviceId,
                                                           @RequestParam String startDate,
                                                           @RequestParam String endDate,
                                                           @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone) {
        try {
            boolean timeline = TIMELINE_DEVICE_ID.equals(deviceId) && dataManagementEnabled;
            Device device = timeline ? null : findDevice(user, deviceId);
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            ZonedDateTime startDateTime = start.atStartOfDay(timezone);
            ZonedDateTime endDateTime = end.plusDays(1).atStartOfDay(timezone);

            String filename = String.format("location_data_%s_to_%s.gpx",
                start.format(DateTimeFormatter.ISO_LOCAL_DATE),
                end.format(DateTimeFormatter.ISO_LOCAL_DATE));

            StreamingResponseBody stream = outputStream -> {
                try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                    if (timeline) {
                        gpxExportService.generateTimelineGpxContentStreaming(user, startDateTime.toInstant(), endDateTime.toInstant(), writer);
                    } else {
                        gpxExportService.generateGpxContentStreaming(user, device, startDateTime.toInstant(), endDateTime.toInstant(), writer);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Error generating GPX file", e);
                }
            };
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
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

    public record DataLine(LocalDateTime timestamp, double latitude, double longitude, double accuracyMeters) {}

}
