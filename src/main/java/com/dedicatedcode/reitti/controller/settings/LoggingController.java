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
    
}
