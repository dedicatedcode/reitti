package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.service.GpxExportService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@RequestMapping("/settings/export-data")
public class ExportDataController {
    
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final GpxExportService gpxExportService;
    private final boolean dataManagementEnabled;

    public ExportDataController(RawLocationPointJdbcService rawLocationPointJdbcService,
                                GpxExportService gpxExportService,
                                @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled) {
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.gpxExportService = gpxExportService;
        this.dataManagementEnabled = dataManagementEnabled;
    }


    @GetMapping
    public void getExportDataContent(@AuthenticationPrincipal User user, Model model) {
        // Set default date range to today
        java.time.LocalDate today = java.time.LocalDate.now();
        model.addAttribute("startDate", today);
        model.addAttribute("endDate", today);

        // Get raw location points for today by default
        List<RawLocationPoint> rawLocationPoints = rawLocationPointJdbcService.findByUserAndDateRange(
                user, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        model.addAttribute("rawLocationPoints", rawLocationPoints);
        model.addAttribute("activeSection", "export-data");
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
    }

    @GetMapping("/data-content")
    public String getExportDataContent(@AuthenticationPrincipal User user,
                                      @RequestParam(required = false) String startDate,
                                      @RequestParam(required = false) String endDate,
                                      Model model) {
        
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : LocalDate.now();
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
        
        model.addAttribute("startDate", start);
        model.addAttribute("endDate", end);
        
        // Get raw location points for the date range
        List<RawLocationPoint> rawLocationPoints = rawLocationPointJdbcService.findByUserAndDateRange(
            user, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        model.addAttribute("rawLocationPoints", rawLocationPoints);
        
        return "settings/export-data :: export-data-content";
    }
    
    @PostMapping("/gpx")
    public ResponseEntity<StreamingResponseBody> exportGpx(@AuthenticationPrincipal User user,
                                                          @RequestParam String startDate,
                                                           @RequestParam String endDate) {
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            
            String filename = String.format("location_data_%s_to_%s.gpx", 
                start.format(DateTimeFormatter.ISO_LOCAL_DATE),
                end.format(DateTimeFormatter.ISO_LOCAL_DATE));
            
            StreamingResponseBody stream = outputStream -> {
                try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                    gpxExportService.generateGpxContentStreaming(user, start, end, writer);
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

}
