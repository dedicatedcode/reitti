package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.event.TriggerProcessingEvent;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.service.I18nService;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.jobs.JobType;
import com.github.kagkarlsson.scheduler.task.Task;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@Controller
public class ManageDataController {

    private final boolean dataManagementEnabled;
    private final boolean deleteAllHostnameVerificationEnabled;
    private final TripJdbcService tripJdbcService;
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final Task<TriggerProcessingEvent> processingTask;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final I18nService i18n;
    private final JobSchedulingService jobScheduler;

    public ManageDataController(@Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled,
                                @Value("${reitti.data-management.delete-all.hostname-verification.enabled:true}") boolean deleteAllHostnameVerificationEnabled,
                                TripJdbcService tripJdbcService,
                                ProcessedVisitJdbcService processedVisitJdbcService,
                                Task<TriggerProcessingEvent> processingTask,
                                RawLocationPointJdbcService rawLocationPointJdbcService,
                                UserSettingsJdbcService userSettingsJdbcService,
                                I18nService i18nService,
                                JobSchedulingService jobScheduler) {
        this.dataManagementEnabled = dataManagementEnabled;
        this.deleteAllHostnameVerificationEnabled = deleteAllHostnameVerificationEnabled;
        this.tripJdbcService = tripJdbcService;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.processingTask = processingTask;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.i18n = i18nService;
        this.jobScheduler = jobScheduler;
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
    public String processVisitsTrips(@AuthenticationPrincipal User user, Model model) {
        if (!dataManagementEnabled) {
            throw new RuntimeException("Data management is not enabled");
        }

        try {
            UUID parentJob = this.jobScheduler.createParentJob(user, JobType.LOCATION_PROCESSING, "Manual processing");
            jobScheduler.enqueueTask(processingTask, new TriggerProcessingEvent(user.getUsername(), null, null, parentJob),
                                     JobSchedulingService.Metadata.builder()
                                             .user(user)
                                             .friendlyName("Manual processing")
                                             .parentId(parentJob)
                                             .jobType(JobType.LOCATION_PROCESSING).build());
            model.addAttribute("successMessage", i18n.translate("data.process.success"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", i18n.translate("data.process.error", e.getMessage()));
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
            UUID parentJob = this.jobScheduler.createParentJob(user, JobType.LOCATION_PROCESSING, "Manual processing");
            this.jobScheduler.enqueueTask(processingTask, new TriggerProcessingEvent(user.getUsername(), null, null, parentJob),
                                 JobSchedulingService.Metadata.builder()
                                         .user(user)
                                         .friendlyName("Manual processing")
                                         .parentId(parentJob)
                                         .jobType(JobType.LOCATION_PROCESSING).build());
            model.addAttribute("successMessage", i18n.translate("data.clear.reprocess.success"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", i18n.translate("data.clear.reprocess.error", e.getMessage()));
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
                model.addAttribute("errorMessage", i18n.translate("data.remove.all.error.hostname-verification", expectedHostname));
                // Re-add attributes needed for the view
                model.addAttribute("deleteAllRequiresVerification", true);
                model.addAttribute("serverHostname", expectedHostname);
                return "settings/manage-data :: manage-data-content";
            }
        }

        try {
            removeAllDataExceptPlaces(user);
            model.addAttribute("successMessage", i18n.translate("data.remove.all.success"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", i18n.translate("data.remove.all.error", e.getMessage()));
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

}
