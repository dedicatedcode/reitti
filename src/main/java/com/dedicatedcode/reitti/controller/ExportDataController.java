package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
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
@RequestMapping("/export")
public class ExportDataController {
    
    private final RawLocationPointJdbcService rawLocationPointJdbcService;

    public ExportDataController(RawLocationPointJdbcService rawLocationPointJdbcService) {
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
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
                    generateGpxContentStreaming(user, start, end, writer);
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

    // Writing XML as strings is necessary for streaming functionality to avoid loading entire DOM into memory
    private void generateGpxContentStreaming(User user, LocalDate startDate, LocalDate endDate, Writer writer) throws IOException {
        // Write GPX header
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        writer.write("<gpx version=\"1.1\" creator=\"Reitti\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        writer.write("  <metadata>\n");
        writer.write("    <name>Location Data Export</name>\n");
        writer.write("    <desc>Exported location data from " + startDate + " to " + endDate + "</desc>\n");
        writer.write("  </metadata>\n");
        writer.write("  <trk>\n");
        writer.write("    <name>Location Track</name>\n");
        writer.write("    <trkseg>\n");
        
        // Stream location points in batches to avoid loading all into memory
        LocalDate currentDate = startDate;
        
        while (!currentDate.isAfter(endDate)) {
            LocalDate nextDate = currentDate.plusDays(1);
            
            List<RawLocationPoint> points = rawLocationPointJdbcService.findByUserAndDateRange(
                user, currentDate.atStartOfDay(), nextDate.atStartOfDay());
            
            for (RawLocationPoint point : points) {
                writer.write("      <trkpt lat=\"" + point.getLatitude() + "\" lon=\"" + point.getLongitude() + "\">\n");
                writer.write("        <time>" + point.getTimestamp().toString() + "</time>\n");
                
                if (point.getAccuracyMeters() != null) {
                    writer.write("        <extensions>\n");
                    writer.write("          <accuracy>" + point.getAccuracyMeters() + "</accuracy>\n");
                    writer.write("        </extensions>\n");
                }
                
                writer.write("      </trkpt>\n");
            }
            
            writer.flush(); // Flush periodically
            currentDate = nextDate;
        }
        
        // Write GPX footer
        writer.write("    </trkseg>\n");
        writer.write("  </trk>\n");
        writer.write("</gpx>");
        writer.flush();
    }
}
