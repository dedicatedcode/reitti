package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.*;
import com.dedicatedcode.reitti.service.processing.ProcessingPipelineTrigger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class ManageDataController {

    private final boolean dataManagementEnabled;
    private final boolean deleteAllHostnameVerificationEnabled;
    private final TripJdbcService tripJdbcService;
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final ProcessingPipelineTrigger processingPipelineTrigger;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final MessageSource messageSource;

    public ManageDataController(@Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled,
                                @Value("${reitti.data-management.delete-all.hostname-verification.enabled:false}") boolean deleteAllHostnameVerificationEnabled,
                                TripJdbcService tripJdbcService,
                                ProcessedVisitJdbcService processedVisitJdbcService,
                                ProcessingPipelineTrigger processingPipelineTrigger,
                                RawLocationPointJdbcService rawLocationPointJdbcService,
                                UserSettingsJdbcService userSettingsJdbcService,
                                MessageSource messageSource) {
        this.dataManagementEnabled = dataManagementEnabled;
        this.deleteAllHostnameVerificationEnabled = deleteAllHostnameVerificationEnabled;
        this.tripJdbcService = tripJdbcService;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.processingPipelineTrigger = processingPipelineTrigger;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.messageSource = messageSource;
    }

    @GetMapping("/settings/manage-data")
    public String getPage(@AuthenticationPrincipal User user, Model model, HttpServletRequest request) {
        if (!dataManagementEnabled) {
            throw new RuntimeException("Data management is not enabled");
        }
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("activeSection", "manage-data");
        model.addAttribute("dataManagementEnabled", true);
        
        // Add verification info
        model.addAttribute("deleteAllRequiresVerification", deleteAllHostnameVerificationEnabled);
        if (deleteAllHostnameVerificationEnabled) {
            model.addAttribute("serverHostname", request.getServerName());
        }
        
        return "settings/manage-data";
    }

    @GetMapping("/settings/manage-data-content")
    public String getManageDataContent(HttpServletRequest request, Model model) {
        if (!dataManagementEnabled) {
            throw new RuntimeException("Data management is not enabled");
        }
        // Ensure verification info is available if fragment is loaded directly
        model.addAttribute("deleteAllRequiresVerification", deleteAllHostnameVerificationEnabled);
        if (deleteAllHostnameVerificationEnabled) {
            model.addAttribute("serverHostname", request.getServerName());
        }
        return "settings/manage-data :: manage-data-content";
    }

    @PostMapping("/settings/manage-data/process-visits-trips")
    public String processVisitsTrips(Model model) {
        if (!dataManagementEnabled) {
            throw new RuntimeException("Data management is not enabled");
        }

        try {
            processingPipelineTrigger.start();
            model.addAttribute("successMessage", getMessage("data.process.success"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("data.process.error", e.getMessage()));
        }

        return "settings/manage-data :: manage-data-content";
    }

    @PostMapping("/settings/manage-data/clear-and-reprocess")
    public String clearAndReprocess(@AuthenticationPrincipal User user, Model model) {
        if (!dataManagementEnabled) {
            throw new RuntimeException("Data management is not enabled");
        }

        try {
            clearProcessedDataExceptPlaces(user);
            markRawLocationPointsAsUnprocessed(user);
            processingPipelineTrigger.start();
            model.addAttribute("successMessage", getMessage("data.clear.reprocess.success"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("data.clear.reprocess.error", e.getMessage()));
        }

        return "settings/manage-data :: manage-data-content";
    }

    @PostMapping("/settings/manage-data/remove-all-data")
    public String removeAllData(@AuthenticationPrincipal User user, Model model, HttpServletRequest request, @RequestParam(value = "hostname", required = false) String hostname) {
        if (!dataManagementEnabled) {
            throw new RuntimeException("Data management is not enabled");
        }

        // Hostname verification check
        if (deleteAllHostnameVerificationEnabled) {
            String expectedHostname = request.getServerName();
            if (hostname == null || !hostname.trim().equals(expectedHostname)) {
                model.addAttribute("errorMessage", "Hostname verification failed. Please enter the correct hostname.");
                // Re-add attributes needed for the view
                model.addAttribute("deleteAllRequiresVerification", true);
                model.addAttribute("serverHostname", expectedHostname);
                return "settings/manage-data :: manage-data-content";
            }
        }

        try {
            removeAllDataExceptPlaces(user);
            model.addAttribute("successMessage", getMessage("data.remove.all.success"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("data.remove.all.error", e.getMessage()));
        }

        // Re-add attributes needed for the view
        model.addAttribute("deleteAllRequiresVerification", deleteAllHostnameVerificationEnabled);
        if (deleteAllHostnameVerificationEnabled) {
            model.addAttribute("serverHostname", request.getServerName());
        }

        return "settings/manage-data :: manage-data-content";
    }

    private void clearProcessedDataExceptPlaces(User user) {
        tripJdbcService.deleteAllForUser(user);
        processedVisitJdbcService.deleteAllForUser(user);
    }

    private void markRawLocationPointsAsUnprocessed(User user) {
        rawLocationPointJdbcService.markAllAsUnprocessedForUser(user);
    }

    private void removeAllDataExceptPlaces(User user) {
        this.userSettingsJdbcService.deleteNewestData(user);
        tripJdbcService.deleteAllForUser(user);
        processedVisitJdbcService.deleteAllForUser(user);
        rawLocationPointJdbcService.deleteAllForUser(user);
    }

    private String getMessage(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
