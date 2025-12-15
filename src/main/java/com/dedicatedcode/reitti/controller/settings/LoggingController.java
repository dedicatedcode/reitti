package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.logging.LoggingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Controller
@RequestMapping("/settings/logging")
public class LoggingController {
    
    private final LoggingService loggingService;
    private final boolean dataManagementEnabled;

    public LoggingController(LoggingService loggingService,
                             @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled) {
        this.loggingService = loggingService;
        this.dataManagementEnabled = dataManagementEnabled;
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
        return loggingService.createLogStream();
    }
    
    @PostMapping("/update")
    public String updateLoggingSettings(@RequestParam("logger") String logger,
                                       @RequestParam("level") String level,
                                       @RequestParam("size") int size,
                                       @AuthenticationPrincipal User user,
                                       Model model) {
        try {
            String loggerName = (logger == null || logger.trim().isEmpty()) ? "ROOT" : logger.trim();
            loggingService.setLoggerLevel(loggerName, level);
            loggingService.setBufferSize(size);
            
            // Refresh model attributes for the fragment
            model.addAttribute("currentBufferSize", loggingService.getCurrentBufferSize());
            model.addAttribute("maxBufferSize", loggingService.getMaxBufferSize());
            model.addAttribute("currentLogLevel", loggingService.getCurrentLogLevel());
            model.addAttribute("configuredLoggers", loggingService.getAllConfiguredLoggers());
            
            return "settings/logging :: logging-settings-card";
        } catch (Exception e) {
            // For errors, we could return an error fragment or handle differently
            model.addAttribute("error", "Error updating logging settings: " + e.getMessage());
            model.addAttribute("currentBufferSize", loggingService.getCurrentBufferSize());
            model.addAttribute("maxBufferSize", loggingService.getMaxBufferSize());
            model.addAttribute("currentLogLevel", loggingService.getCurrentLogLevel());
            model.addAttribute("configuredLoggers", loggingService.getAllConfiguredLoggers());
            return "settings/logging :: logging-settings-card";
        }
    }
    
    @PostMapping("/remove")
    public String removeLogger(@RequestParam("logger") String logger,
                              @AuthenticationPrincipal User user,
                              Model model) {
        try {
            loggingService.removeLogger(logger);
            
            // Refresh model attributes for the fragment
            model.addAttribute("currentBufferSize", loggingService.getCurrentBufferSize());
            model.addAttribute("maxBufferSize", loggingService.getMaxBufferSize());
            model.addAttribute("currentLogLevel", loggingService.getCurrentLogLevel());
            model.addAttribute("configuredLoggers", loggingService.getAllConfiguredLoggers());
            
            return "settings/logging :: logging-settings-card";
        } catch (Exception e) {
            model.addAttribute("error", "Error removing logger: " + e.getMessage());
            model.addAttribute("currentBufferSize", loggingService.getCurrentBufferSize());
            model.addAttribute("maxBufferSize", loggingService.getMaxBufferSize());
            model.addAttribute("currentLogLevel", loggingService.getCurrentLogLevel());
            model.addAttribute("configuredLoggers", loggingService.getAllConfiguredLoggers());
            return "settings/logging :: logging-settings-card";
        }
    }
    
}
