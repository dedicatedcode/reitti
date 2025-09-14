package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import com.dedicatedcode.reitti.repository.VisitJdbcService;
import com.dedicatedcode.reitti.service.processing.ProcessingPipelineTrigger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class ManageDataController {

    private final boolean dataManagementEnabled;
    private final VisitJdbcService visitJdbcService;
    private final TripJdbcService tripJdbcService;
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final ProcessingPipelineTrigger processingPipelineTrigger;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final MessageSource messageSource;

    public ManageDataController(@Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled,
                                VisitJdbcService visitJdbcService,
                                TripJdbcService tripJdbcService,
                                ProcessedVisitJdbcService processedVisitJdbcService,
                                ProcessingPipelineTrigger processingPipelineTrigger, RawLocationPointJdbcService rawLocationPointJdbcService,
                                MessageSource messageSource) {
        this.dataManagementEnabled = dataManagementEnabled;
        this.visitJdbcService = visitJdbcService;
        this.tripJdbcService = tripJdbcService;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.processingPipelineTrigger = processingPipelineTrigger;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.messageSource = messageSource;
    }

    @GetMapping("/settings/manage-data")
    public String getPage(@AuthenticationPrincipal User user, Model model) {
        if (!dataManagementEnabled) {
            throw new RuntimeException("Data management is not enabled");
        }
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("activeSection", "manage-data");
        model.addAttribute("dataManagementEnabled", true);
        return "settings/manage-data";
    }

    @GetMapping("/settings/manage-data-content")
    public String getManageDataContent() {
        if (!dataManagementEnabled) {
            throw new RuntimeException("Data management is not enabled");
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
            // Clear all processed data except SignificantPlaces
            // This would need to be implemented in appropriate service classes
            // For now, we'll assume these methods exist or need to be created
            clearProcessedDataExceptPlaces(user);

            // Mark all raw location points as unprocessed
            markRawLocationPointsAsUnprocessed(user);

            processingPipelineTrigger.start();

            model.addAttribute("successMessage", getMessage("data.clear.reprocess.success"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("data.clear.reprocess.error", e.getMessage()));
        }

        return "settings/manage-data :: manage-data-content";
    }

    @PostMapping("/settings/manage-data/remove-all-data")
    public String removeAllData(@AuthenticationPrincipal User user, Model model) {
        if (!dataManagementEnabled) {
            throw new RuntimeException("Data management is not enabled");
        }

        try {
            removeAllDataExceptPlaces(user);

            model.addAttribute("successMessage", getMessage("data.remove.all.success"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("data.remove.all.error", e.getMessage()));
        }

        return "settings/manage-data :: manage-data-content";
    }

    private void clearProcessedDataExceptPlaces(User user) {
        tripJdbcService.deleteAllForUser(user);
        processedVisitJdbcService.deleteAllForUser(user);
        visitJdbcService.deleteAllForUser(user);
    }

    private void markRawLocationPointsAsUnprocessed(User user) {
        rawLocationPointJdbcService.markAllAsUnprocessedForUser(user);
    }

    private void removeAllDataExceptPlaces(User user) {
        tripJdbcService.deleteAllForUser(user);
        processedVisitJdbcService.deleteAllForUser(user);
        visitJdbcService.deleteAllForUser(user);
        rawLocationPointJdbcService.deleteAllForUser(user);
    }

    private String getMessage(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
