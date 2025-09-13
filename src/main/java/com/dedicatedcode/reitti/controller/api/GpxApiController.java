package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.GpxExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/v1/gpx")
public class GpxApiController {
    
    private final GpxExportService gpxExportService;

    public GpxApiController(GpxExportService gpxExportService) {
        this.gpxExportService = gpxExportService;
    }

    @GetMapping("/export")
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
