package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.logging.LoggingService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
public class LoggingController {
    
    private final LoggingService loggingService;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final boolean dataManagementEnabled;

    public LoggingController(LoggingService loggingService,
                             @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled) {
        this.loggingService = loggingService;
        this.dataManagementEnabled = dataManagementEnabled;
    }
    
    @PostConstruct
    public void init() {
        // Add shutdown hook as fallback in case @PreDestroy is not called
        Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup));
    }
    
    @GetMapping
    public String loggingPage(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("activeSection", "logging");
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("currentBufferSize", loggingService.getCurrentBufferSize());
        model.addAttribute("maxBufferSize", loggingService.getMaxBufferSize());
        model.addAttribute("currentLogLevel", loggingService.getCurrentLogLevel());
        model.addAttribute("configuredLoggers", loggingService.getAllConfiguredLoggers());
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
    
    @PostMapping("/update")
    @ResponseBody
    public ResponseEntity<String> updateLoggingSettings(@RequestParam("logger") String logger,
                                                       @RequestParam("level") String level,
                                                       @RequestParam("size") int size) {
        try {
            String loggerName = (logger == null || logger.trim().isEmpty()) ? "ROOT" : logger.trim();
            loggingService.setLoggerLevel(loggerName, level);
            loggingService.setBufferSize(size);
            return ResponseEntity.ok("Logging settings updated");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error updating logging settings: " + e.getMessage());
        }
    }
    
    @PostMapping("/remove")
    @ResponseBody
    public ResponseEntity<String> removeLogger(@RequestParam("logger") String logger) {
        try {
            loggingService.removeLogger(logger);
            return ResponseEntity.ok("Logger removed");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error removing logger: " + e.getMessage());
        }
    }
    
    @PreDestroy
    public void cleanup() {
        // Close all SSE connections to prevent blocking shutdown
        for (SseEmitter emitter : emitters) {
            try {
                emitter.complete();
            } catch (Exception e) {
                // Ignore exceptions during cleanup
            }
        }
        emitters.clear();
    }
    
    private String formatLogLineForHtml(String logLine) {
        String escaped = logLine
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
        
        return "<div class=\"log-line\">" + escaped + "</div>";
    }
}
