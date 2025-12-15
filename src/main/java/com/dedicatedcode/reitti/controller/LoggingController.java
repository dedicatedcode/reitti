package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.service.logging.LoggingService;
import com.fasterxml.jackson.databind.JsonNode;
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
        
        // Send existing log buffer first
        try {
            List<String> snapshot = loggingService.getLogSnapshot();
            for (String logLine : snapshot) {
                emitter.send(SseEmitter.event()
                        .name("log")
                        .data(formatLogLineForHtml(logLine)));
            }
        } catch (IOException e) {
            emitters.remove(emitter);
            emitter.completeWithError(e);
            return emitter;
        }
        
        // Set up listener for new log events
        Consumer<String> listener = logLine -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("log")
                        .data(formatLogLineForHtml(logLine)));
            } catch (IOException e) {
                emitters.remove(emitter);
                loggingService.getLogAppender().removeListener(this);
                emitter.completeWithError(e);
            }
        };
        
        loggingService.getLogAppender().addListener(listener);
        
        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            loggingService.getLogAppender().removeListener(listener);
        });
        
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            loggingService.getLogAppender().removeListener(listener);
        });
        
        emitter.onError((ex) -> {
            emitters.remove(emitter);
            loggingService.getLogAppender().removeListener(listener);
        });
        
        return emitter;
    }
    
    @PostMapping("/level")
    @ResponseBody
    public ResponseEntity<String> setLogLevel(@RequestBody JsonNode request) {
        try {
            String logger = request.get("logger").asText();
            String level = request.get("level").asText();
            
            loggingService.setLoggerLevel(logger, level);
            return ResponseEntity.ok("Log level updated");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error updating log level: " + e.getMessage());
        }
    }
    
    @PostMapping("/buffer")
    @ResponseBody
    public ResponseEntity<String> setBufferSize(@RequestBody JsonNode request) {
        try {
            int size = request.get("size").asInt();
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
