package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.RawLocationPoint;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@RequestMapping("/export")
public class ExportDataController {
    
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final MessageSource messageSource;
    
    public ExportDataController(RawLocationPointJdbcService rawLocationPointJdbcService,
                               MessageSource messageSource) {
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.messageSource = messageSource;
    }
    
    private String getMessage(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
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
        
        return "fragments/export-data :: export-data-content";
    }
    
    @PostMapping("/gpx")
    public ResponseEntity<String> exportGpx(@AuthenticationPrincipal User user,
                                           @RequestParam String startDate,
                                           @RequestParam String endDate) {
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            
            List<RawLocationPoint> rawLocationPoints = rawLocationPointJdbcService.findByUserAndDateRange(
                user, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
            
            String gpxContent = generateGpxContent(rawLocationPoints, start, end);
            
            String filename = String.format("location_data_%s_to_%s.gpx", 
                start.format(DateTimeFormatter.ISO_LOCAL_DATE),
                end.format(DateTimeFormatter.ISO_LOCAL_DATE));
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_XML)
                .body(gpxContent);
                
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body("Error generating GPX file: " + e.getMessage());
        }
    }
    
    private String generateGpxContent(List<RawLocationPoint> points, LocalDate startDate, LocalDate endDate) {
        StringBuilder gpx = new StringBuilder();
        
        gpx.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        gpx.append("<gpx version=\"1.1\" creator=\"Reitti\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        gpx.append("  <metadata>\n");
        gpx.append("    <name>Location Data Export</name>\n");
        gpx.append("    <desc>Exported location data from ").append(startDate).append(" to ").append(endDate).append("</desc>\n");
        gpx.append("  </metadata>\n");
        gpx.append("  <trk>\n");
        gpx.append("    <name>Location Track</name>\n");
        gpx.append("    <trkseg>\n");
        
        for (RawLocationPoint point : points) {
            gpx.append("      <trkpt lat=\"").append(point.getLatitude()).append("\" lon=\"").append(point.getLongitude()).append("\">\n");
            gpx.append("        <time>").append(point.getTimestamp().toString()).append("</time>\n");
            if (point.getAccuracyMeters() != null) {
                gpx.append("        <extensions>\n");
                gpx.append("          <accuracy>").append(point.getAccuracyMeters()).append("</accuracy>\n");
                gpx.append("        </extensions>\n");
            }
            gpx.append("      </trkpt>\n");
        }
        
        gpx.append("    </trkseg>\n");
        gpx.append("  </trk>\n");
        gpx.append("</gpx>");
        
        return gpx.toString();
    }
}
