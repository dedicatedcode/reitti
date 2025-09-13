package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.GpxExportService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/gpx")
public class GpxApiController {
    
    private final GpxExportService gpxExportService;

    public GpxApiController(GpxExportService gpxExportService) {
        this.gpxExportService = gpxExportService;
    }

    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> exportGpx(@AuthenticationPrincipal User user,
                                                          @RequestParam LocalDate start,
                                                          @RequestParam LocalDate end) {
        try {
            StreamingResponseBody stream = outputStream -> {
                try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                    gpxExportService.generateGpxContentStreaming(user, start, end, writer);
                } catch (Exception e) {
                    throw new RuntimeException("Error generating GPX file", e);
                }
            };
            
            return ResponseEntity.ok()
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
