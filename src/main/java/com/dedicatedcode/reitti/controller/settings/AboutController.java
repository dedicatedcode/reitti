package com.dedicatedcode.reitti.controller.settings;


import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.VersionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/settings")
public class AboutController {
    private final VersionService versionService;
    private final boolean dataManagementEnabled;

    public AboutController(VersionService versionService,
                           @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled) {
        this.versionService = versionService;
        this.dataManagementEnabled = dataManagementEnabled;
    }

    @GetMapping("/about")
    public String getPage(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        model.addAttribute("activeSection", "about");
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("buildVersion", this.versionService.getVersion());
        model.addAttribute("gitCommitDetails", this.versionService.getCommitDetails());
        model.addAttribute("buildTime", this.versionService.getBuildTime());
        return "settings/about";
    }

}
