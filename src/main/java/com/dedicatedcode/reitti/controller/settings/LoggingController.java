package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.service.logging.LoggingService;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Controller
@RequestMapping("/settings/logging")
@PreAuthorize("hasRole('ADMIN')")
public class LoggingController {
    
    private final LoggingService loggingService;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    @Autowired
    public LoggingController(LoggingService loggingService) {
        this.loggingService = loggingService;
    }
    
    @GetMapping
    public String loggingPage(Model model) {
        model.addAttribute("activeSection", "logging");
        model.addAttribute("dataManagementEnabled", false);
        model.addAttribute("isAdmin", true);
        model.addAttribute("currentBufferSize", loggingService.getCurrentBufferSize());
        model.addAttribute("maxBufferSize", loggingService.getMaxBufferSize());
        return "settings/logging";
    }
    
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.add(emitter);
        try {
            List<String> snapshot = loggingService.getLogSnapshot();
            for (String logLine : snapshot) {
                emitter.send(SseEmitter.event()
                        .name("log")
                        .data(formatLogLineForHtml(logLine)));
            }
        } catch (IOException e) {
            emitter.completeWithError(e);
            return emitter;
        }
        
        Consumer<String> listener = logLine -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("log")
                        .data(formatLogLineForHtml(logLine)));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        };
        
        loggingService.getLogAppender().addListener(listener);
        
        emitter.onCompletion(() -> {
            loggingService.getLogAppender().removeListener(listener);
        });
        
        emitter.onTimeout(() -> {
            loggingService.getLogAppender().removeListener(listener);
        });
        
        emitter.onError((ex) -> {
            loggingService.getLogAppender().removeListener(listener);
        });
        
        return emitter;
    }

    @PreDestroy
    public void destroy() {
        emitters.forEach(SseEmitter::complete);
    }
    
    @PostMapping("/level")
    @ResponseBody
    public ResponseEntity<String> setLogLevel(@RequestParam("logger") String logger, 
                                            @RequestParam("level") String level) {
        try {
            // Handle empty logger as ROOT
            String loggerName = (logger == null || logger.trim().isEmpty()) ? "ROOT" : logger.trim();
            loggingService.setLoggerLevel(loggerName, level);
            return ResponseEntity.ok("Log level updated");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error updating log level: " + e.getMessage());
        }
    }
    
    @PostMapping("/buffer")
    @ResponseBody
    public ResponseEntity<String> setBufferSize(@RequestParam("size") int size) {
        try {
            loggingService.setBufferSize(size);
            return ResponseEntity.ok("Buffer size updated");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error updating buffer size: " + e.getMessage());
        }
    }
    
    private String formatLogLineForHtml(String logLine) {
        // Escape HTML and preserve formatting
        String escaped = logLine
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
        
        return "<div class=\"log-line\">" + escaped + "</div>";
    }
}
