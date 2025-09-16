package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.QueueStatsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;


@Controller
@RequestMapping("/settings")
public class SettingsController {
    private final QueueStatsService queueStatsService;
    private final boolean dataManagementEnabled;

    public SettingsController(QueueStatsService queueStatsService,
                              @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled) {
        this.queueStatsService = queueStatsService;
        this.dataManagementEnabled = dataManagementEnabled;
    }


    @GetMapping
    public String settingsPage() {
        return "redirect:/settings/job-status";
    }


    @GetMapping("/job-status")
    public String getJobStatus(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("activeSection", "job-status");
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        model.addAttribute("queueStats", queueStatsService.getQueueStats());
        return "settings/job-status";
    }

    @GetMapping("/queue-stats-content")
    public String getQueueStatsContent(Model model) {
        model.addAttribute("queueStats", queueStatsService.getQueueStats());
        return "settings/job-status :: queue-stats-content";
    }
}
